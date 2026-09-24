package com.landvoigtit.stackit.resourceexplorer.compute;

import cloud.stackit.sdk.iaas.v1api.model.Server;
import cloud.stackit.sdk.iaas.v1api.model.ServerNetwork;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class ComputeResourceMapperTest {

    @Test
    public void testMapToDtoAndEntity_WithPublicIp() {
        final UUID serverId = UUID.randomUUID();
        final ServerNetwork nic = new ServerNetwork();
        nic.setIpv4("192.168.1.10");
        nic.setPublicIp("193.148.160.5");

        final Server server = new Server(
                OffsetDateTime.parse("2026-03-01T10:00:00Z"),
                null,
                serverId,
                OffsetDateTime.parse("2026-03-01T10:05:30Z"),
                null,
                List.of(nic),
                "RUNNING",
                "ACTIVE",
                OffsetDateTime.parse("2026-03-01T10:05:30Z")
        );
        server.setName("vm-with-public-ip");
        server.setMachineType("g1a.2");
        server.setAvailabilityZone("eu01-1");

        final ComputeResourceDto dto = ComputeResourceMapper.mapToDto(server);
        assertNotNull(dto);
        assertEquals(List.of("192.168.1.10", "193.148.160.5"), dto.getIpAddresses());
        assertNotNull(dto.getPublicIps());
        assertEquals(List.of("193.148.160.5"), dto.getPublicIps());

        final StackitEntity entity = ComputeResourceMapper.mapToEntity(dto);
        assertNotNull(entity);
        assertNotNull(entity.getData());
        assertEquals(List.of("193.148.160.5"), entity.getData().get("publicIps"));

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> history = (List<Map<String, Object>>) entity.getData().get("publicIpHistory");
        assertNotNull(history);
        assertEquals(1, history.size());
        assertEquals("193.148.160.5", history.get(0).get("ip"));
        assertEquals("2026-03-01T10:05:30Z", history.get(0).get("firstSeen"));
        assertEquals("2026-03-01T10:05:30Z", history.get(0).get("lastSeen"));
        assertEquals(true, history.get(0).get("active"));
    }

    @Test
    public void testMapToDtoAndEntity_WithoutPublicIp() {
        final UUID serverId = UUID.randomUUID();
        final ServerNetwork nic = new ServerNetwork();
        nic.setIpv4("10.0.0.5");

        final Server server = new Server(
                OffsetDateTime.parse("2026-03-01T10:00:00Z"),
                null,
                serverId,
                OffsetDateTime.parse("2026-03-01T10:05:00Z"),
                null,
                List.of(nic),
                "RUNNING",
                "ACTIVE",
                OffsetDateTime.parse("2026-03-01T10:05:00Z")
        );
        server.setName("vm-private-only");

        final ComputeResourceDto dto = ComputeResourceMapper.mapToDto(server);
        assertNotNull(dto);
        assertEquals(List.of("10.0.0.5"), dto.getIpAddresses());
        assertTrue(dto.getPublicIps() == null || dto.getPublicIps().isEmpty());

        final StackitEntity entity = ComputeResourceMapper.mapToEntity(dto);
        assertNotNull(entity);
        assertNotNull(entity.getData());
        assertNull(entity.getData().get("publicIps"));
        assertNull(entity.getData().get("publicIpHistory"));
    }

    @Test
    public void testMapToDto_DeduplicatesMultipleNicsSamePublicIp() {
        final UUID serverId = UUID.randomUUID();
        final ServerNetwork nic1 = new ServerNetwork();
        nic1.setIpv4("192.168.1.5");
        nic1.setPublicIp("193.148.160.20");

        final ServerNetwork nic2 = new ServerNetwork();
        nic2.setIpv4("192.168.1.6");
        nic2.setPublicIp("193.148.160.20"); // duplicate public IP

        final ServerNetwork nic3 = new ServerNetwork();
        nic3.setIpv4("192.168.1.7");
        nic3.setPublicIp("193.148.160.25"); // second distinct public IP

        final Server server = new Server(
                OffsetDateTime.parse("2026-03-01T10:00:00Z"),
                null,
                serverId,
                OffsetDateTime.parse("2026-03-01T10:05:00Z"),
                null,
                List.of(nic1, nic2, nic3),
                "RUNNING",
                "ACTIVE",
                OffsetDateTime.parse("2026-03-01T10:05:00Z")
        );
        server.setName("vm-multi-nic");

        final ComputeResourceDto dto = ComputeResourceMapper.mapToDto(server);
        assertNotNull(dto);
        assertEquals(List.of("193.148.160.20", "193.148.160.25"), dto.getPublicIps());
    }
}
