package com.landvoigtit.stackit.resourceexplorer;

import cloud.stackit.sdk.iaas.v1api.model.Server;
import cloud.stackit.sdk.iaas.v1api.model.Network;
import cloud.stackit.sdk.iaas.v1api.model.Volume;
import cloud.stackit.sdk.objectstorage.v1api.model.Bucket;
import cloud.stackit.sdk.alb.v2api.model.LoadBalancer;
import cloud.stackit.sdk.resourcemanager.v0api.model.Member;
import com.landvoigtit.stackit.resourceexplorer.compute.ComputeResourceDto;
import com.landvoigtit.stackit.resourceexplorer.compute.ComputeResourceMapper;
import com.landvoigtit.stackit.resourceexplorer.iam.IamResourceDto;
import com.landvoigtit.stackit.resourceexplorer.iam.IamResourceMapper;
import com.landvoigtit.stackit.resourceexplorer.network.NetworkResourceDto;
import com.landvoigtit.stackit.resourceexplorer.network.NetworkResourceMapper;
import com.landvoigtit.stackit.resourceexplorer.network.NetworkVpcResourceDto;
import com.landvoigtit.stackit.resourceexplorer.network.NetworkVpcResourceMapper;
import com.landvoigtit.stackit.resourceexplorer.storage.StorageResourceDto;
import com.landvoigtit.stackit.resourceexplorer.storage.StorageResourceMapper;
import com.landvoigtit.stackit.resourceexplorer.storage.VmDiskResourceDto;
import com.landvoigtit.stackit.resourceexplorer.storage.VmDiskResourceMapper;
import com.landvoigtit.stackit.resourceexplorer.persistence.StackitEntity;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

public class DomainMappersTest {

    @Test
    public final void testComputeMapping() {
        final UUID srvId = UUID.randomUUID();
        final UUID bootVolId = UUID.randomUUID();
        final cloud.stackit.sdk.iaas.v1api.model.BootVolume bootVol = new cloud.stackit.sdk.iaas.v1api.model.BootVolume(bootVolId);
        bootVol.setDeleteOnTermination(true);

        final Server server = new Server(
            java.time.OffsetDateTime.now(),
            null,
            srvId,
            null,
            null,
            null,
            "RUNNING",
            "ACTIVE",
            java.time.OffsetDateTime.now()
        );
        server.setName("sbx-1-vm-1");
        server.setMachineType("g1r.1d");
        server.setAvailabilityZone("eu01-3");
        server.setBootVolume(bootVol);
        server.setVolumes(List.of(bootVolId));
        server.setLabels(java.util.Map.of("cost-center", "4711", "owner", "harald.landvoigt"));

        final ComputeResourceDto dto = ComputeResourceMapper.mapToDto(server);
        assertNotNull(dto);
        assertEquals(srvId.toString(), dto.getServerId());
        assertEquals("sbx-1-vm-1", dto.getName());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals("RUNNING", dto.getPowerStatus());
        assertEquals("g1r.1d", dto.getMachineType());
        assertEquals("eu01-3", dto.getAvailabilityZone());
        assertEquals(bootVolId.toString(), dto.getBootVolumeId());
        assertTrue(dto.getBootVolumeDeleteOnTermination());
        assertEquals(List.of(bootVolId.toString()), dto.getAttachedVolumes());
        assertEquals("4711", dto.getLabels().get("cost-center"));

        final StackitEntity entity = ComputeResourceMapper.mapToEntity(dto);
        assertNotNull(entity);
        assertEquals(srvId, entity.getId());
        assertEquals(srvId.toString(), entity.getResourceId());
        assertEquals("eu01-3", entity.getRegion());
        assertEquals("4711", entity.getTags().get("cost-center"));
        assertEquals("harald.landvoigt", entity.getTags().get("owner"));
        assertEquals("g1r.1d", entity.getData().get("machineType"));
        assertEquals("RUNNING", entity.getData().get("powerStatus"));
        assertEquals(bootVolId.toString(), entity.getData().get("bootVolumeId"));
        assertNull(entity.getData().get("image"));
    }

    @Test
    public final void testStorageMapping() {
        final Bucket bucket = new Bucket();
        final StorageResourceDto dto = StorageResourceMapper.mapToDto(bucket);
        assertNotNull(dto);
    }

    @Test
    public final void testNetworkMapping() {
        final LoadBalancer lb = new LoadBalancer();
        final NetworkResourceDto dto = NetworkResourceMapper.mapToDto(lb);
        assertNotNull(dto);
    }

    @Test
    public final void testNetworkVpcMapping() {
        final Network network = new Network();
        final UUID netId = UUID.randomUUID();
        network.setNetworkId(netId);
        network.setName("prod-vpc");
        network.setState("ACTIVE");
        network.setPrefixes(List.of("192.168.0.0/16"));
        network.setGateway("192.168.0.1");
        network.setRouted(true);

        final NetworkVpcResourceDto dto = NetworkVpcResourceMapper.mapToDto(network);
        assertNotNull(dto);
        assertEquals(netId.toString(), dto.getNetworkId());
        assertEquals("prod-vpc", dto.getName());
        assertEquals("ACTIVE", dto.getStatus());
        assertEquals(List.of("192.168.0.0/16"), dto.getPrefixes());
        assertEquals("192.168.0.1", dto.getGateway());
        assertTrue(dto.getRouted());

        final StackitEntity entity = NetworkVpcResourceMapper.mapToEntity(dto);
        assertNotNull(entity);
        assertEquals(netId, entity.getId());
        assertEquals(netId.toString(), entity.getResourceId());
        assertEquals("prod-vpc", entity.getName());
        assertEquals("network-vpc", entity.getType());
        assertEquals("ACTIVE", entity.getStatus());
        assertNotNull(entity.getData());
        assertEquals(List.of("192.168.0.0/16"), entity.getData().get("prefixes"));
    }

    @Test
    public final void testVmDiskMapping() {
        final UUID volId = UUID.randomUUID();
        final UUID srvId = UUID.randomUUID();
        final Volume volume = new Volume(
            java.time.OffsetDateTime.now(),
            true,
            volId,
            null,
            srvId,
            "AVAILABLE",
            java.time.OffsetDateTime.now()
        );
        volume.setName("boot-disk");
        volume.setSize(50L);
        volume.setPerformanceClass("storage_premium_perf1");
        volume.setAvailabilityZone("eu01-1");
        volume.setBootable(true);

        final VmDiskResourceDto dto = VmDiskResourceMapper.mapToDto(volume);
        assertNotNull(dto);
        assertEquals(volId.toString(), dto.getVolumeId());
        assertEquals("boot-disk", dto.getName());
        assertEquals("AVAILABLE", dto.getStatus());
        assertEquals(50L, dto.getSizeGb());
        assertEquals("storage_premium_perf1", dto.getPerformanceClass());
        assertEquals("eu01-1", dto.getAvailabilityZone());
        assertTrue(dto.getBootable());
        assertTrue(dto.getEncrypted());
        assertEquals(srvId.toString(), dto.getServerId());

        final StackitEntity entity = VmDiskResourceMapper.mapToEntity(dto);
        assertNotNull(entity);
        assertEquals(volId, entity.getId());
        assertEquals(volId.toString(), entity.getResourceId());
        assertEquals("boot-disk", entity.getName());
        assertEquals("vmdisks", entity.getType());
        assertEquals("AVAILABLE", entity.getStatus());
        assertEquals("eu01-1", entity.getRegion());
        assertNotNull(entity.getData());
        assertEquals(50L, entity.getData().get("sizeGb"));
        assertEquals("storage_premium_perf1", entity.getData().get("performanceClass"));
        assertEquals(srvId.toString(), entity.getData().get("serverId"));
    }

    @Test
    public final void testIamMapping() {
        final Member member = new Member();
        final IamResourceDto dto = IamResourceMapper.mapToDto(member);
        assertNotNull(dto);
    }
}
