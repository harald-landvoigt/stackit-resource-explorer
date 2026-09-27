package com.landvoigtit.stackit.resourceexplorer.network;

import cloud.stackit.sdk.iaas.v2api.model.PublicIp;
import com.landvoigtit.stackit.resourceexplorer.config.StackitConstants;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class PublicIpResourceMapperTest {

    @Test
    public void testMapToDto_Null() {
        assertNull(PublicIpResourceMapper.mapToDto(null));
    }

    @Test
    public void testMapToEntity_Null() {
        assertNull(PublicIpResourceMapper.mapToEntity(null));
    }

    @Test
    public void testMapToDto_Attached() {
        final UUID ipId = UUID.randomUUID();
        final UUID nicId = UUID.randomUUID();
        final PublicIp publicIp = new PublicIp(ipId, "193.148.160.10");
        publicIp.setNetworkInterface(nicId);
        publicIp.setLabels(Map.of("env", "prod", "managed-by", "terraform"));

        final PublicIpResourceDto dto = PublicIpResourceMapper.mapToDto(publicIp);

        assertNotNull(dto);
        assertEquals(ipId.toString(), dto.getPublicIpId());
        assertEquals("193.148.160.10", dto.getIp());
        assertEquals("193.148.160.10", dto.getName());
        assertEquals(nicId.toString(), dto.getNetworkInterfaceId());
        assertEquals(true, dto.getAttached());
        assertEquals("ATTACHED", dto.getStatus());
        assertNotNull(dto.getLabels());
        assertEquals("prod", dto.getLabels().get("env"));
    }

    @Test
    public void testMapToDto_Unattached() {
        final UUID ipId = UUID.randomUUID();
        final PublicIp publicIp = new PublicIp(ipId, "193.148.160.25");
        publicIp.setNetworkInterface(null);

        final PublicIpResourceDto dto = PublicIpResourceMapper.mapToDto(publicIp);

        assertNotNull(dto);
        assertEquals(ipId.toString(), dto.getPublicIpId());
        assertEquals("193.148.160.25", dto.getIp());
        assertNull(dto.getNetworkInterfaceId());
        assertEquals(false, dto.getAttached());
        assertEquals("UNATTACHED", dto.getStatus());
    }

    @Test
    public void testMapToEntity_Attached() {
        final String ipId = UUID.randomUUID().toString();
        final PublicIpResourceDto dto = new PublicIpResourceDto();
        dto.setPublicIpId(ipId);
        dto.setIp("193.148.160.10");
        dto.setName("193.148.160.10");
        dto.setStatus("ATTACHED");
        dto.setRegion("eu01");
        dto.setAttached(true);
        dto.setNetworkInterfaceId("nic-1234");
        dto.setServerId("server-5678");
        dto.setServerName("web-gateway-01");
        dto.setLabels(Map.of("role", "edge"));

        final StackitEntity entity = PublicIpResourceMapper.mapToEntity(dto);

        assertNotNull(entity);
        assertEquals(UUID.fromString(ipId), entity.getId());
        assertEquals(ipId, entity.getResourceId());
        assertEquals("193.148.160.10", entity.getName());
        assertEquals(StackitConstants.RESOURCE_TYPE_PUBLIC_IP, entity.getType());
        assertEquals("ATTACHED", entity.getStatus());
        assertEquals("eu01", entity.getRegion());

        assertNotNull(entity.getTags());
        assertEquals("true", entity.getTags().get("attached"));
        assertEquals("193.148.160.10", entity.getTags().get("ip"));
        assertEquals("edge", entity.getTags().get("role"));

        assertNotNull(entity.getData());
        assertEquals("193.148.160.10", entity.getData().get("ip"));
        assertEquals(true, entity.getData().get("attached"));
        assertEquals("nic-1234", entity.getData().get("networkInterfaceId"));
        assertEquals("server-5678", entity.getData().get("serverId"));
        assertEquals("web-gateway-01", entity.getData().get("serverName"));
    }

    @Test
    public void testMapToEntity_Unattached() {
        final String ipId = UUID.randomUUID().toString();
        final PublicIpResourceDto dto = new PublicIpResourceDto();
        dto.setPublicIpId(ipId);
        dto.setIp("193.148.160.99");
        dto.setStatus("UNATTACHED");
        dto.setRegion("eu02");
        dto.setAttached(false);

        final StackitEntity entity = PublicIpResourceMapper.mapToEntity(dto);

        assertNotNull(entity);
        assertEquals("UNATTACHED", entity.getStatus());
        assertEquals("false", entity.getTags().get("attached"));
        assertEquals(false, entity.getData().get("attached"));
        assertNull(entity.getData().get("serverId"));
        assertNull(entity.getData().get("serverName"));
    }
}
