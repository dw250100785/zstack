package org.zstack.simulator.kvm;

import org.zstack.kvm.KVMAgentCommands;
import org.zstack.kvm.KVMAgentCommands.*;
import org.zstack.kvm.KVMConstant.KvmVmState;
import org.zstack.network.securitygroup.SecurityGroupRuleTO;
import org.zstack.utils.data.SizeUnit;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class KVMSimulatorConfig {
    public volatile boolean connectSuccess = true;
    public List<ConnectCmd> connectCmds = new ArrayList<ConnectCmd>();
    public volatile String simulatorHostUuid;
    public Map<String, String> connectHostUuids = new HashMap<String, String>();
    public volatile boolean connectException = false;
    public volatile boolean pingSuccess = true;
    public volatile Map<String, Boolean> pingSuccessMap = new HashMap<String, Boolean>();
    public volatile boolean mountSuccess = true;
    public volatile boolean mountException = false;
    public volatile long totalDiskCapacity = SizeUnit.TERABYTE.toByte(100);
    public volatile long availableDiskCapacity = totalDiskCapacity;
    public volatile boolean unmountSuccess = true;
    public volatile boolean unmountException = false;
    public volatile boolean hostFactSuccess = true;
    public volatile boolean hostFactException = false;
    public volatile long cpuNum = 1024;
    public volatile long cpuSpeed = 26000;
    public volatile long usedCpu;
    public volatile long totalMemory = SizeUnit.GIGABYTE.toByte(256);
    public volatile long usedMemory;
    public volatile boolean checkPhysicalInterfaceSuccess = true;
    public volatile boolean checkPhysicalInterfaceException = false;
    public volatile boolean createL2NoVlanNetworkSuccess = true;
    public volatile boolean createL2VlanNetworkSuccess = true;
    public volatile boolean startVmSuccess = true;
    public volatile boolean stopVmSuccess = true;
    public volatile List<StopVmCmd> stopVmCmds = new ArrayList<StopVmCmd>();
    public volatile boolean rebootVmSuccess = true;
    public volatile List<RebootVmCmd> rebootVmCmds = new ArrayList<RebootVmCmd>();
    public volatile boolean destroyVmSuccess = true;
    public volatile boolean attachVolumeSuccess = true;
    public volatile boolean detachVolumeSuccess = true;
    public volatile List<DetachDataVolumeCmd> detachDataVolumeCmds = new ArrayList<DetachDataVolumeCmd>();
    public volatile String destroyedVmUuid;
    public volatile boolean securityGroupSuccess = true;
    public volatile Map<String, SecurityGroupRuleTO> securityGroups = new HashMap<String, SecurityGroupRuleTO>();
    public volatile List<RefreshAllRulesOnHostCmd> securityGroupRefreshAllRulesOnHostCmds = new ArrayList<RefreshAllRulesOnHostCmd>();
    public volatile Map<String, KvmVmState> vms = new HashMap<String, KvmVmState>();
    public volatile int consolePort;
    public volatile KVMAgentCommands.StartVmCmd startVmCmd;
    public volatile Map<String, KVMAgentCommands.NicTO> attachedNics = new HashMap<String, KVMAgentCommands.NicTO>();
    public volatile boolean attachNicSuccess = true;
    public volatile List<KVMAgentCommands.CreateBridgeCmd> bridges = new ArrayList<KVMAgentCommands.CreateBridgeCmd>();
    public volatile List<KVMAgentCommands.CreateVlanBridgeCmd> vlanBridges = new ArrayList<KVMAgentCommands.CreateVlanBridgeCmd>();
    public volatile boolean checkNoVlanBridgeSuccess = true;
    public volatile boolean checkVlanBridgeSuccess = true;
    public volatile boolean migrateVmSuccess = true;
    public volatile List<KVMAgentCommands.MigrateVmCmd> migrateVmCmds = new ArrayList<KVMAgentCommands.MigrateVmCmd>();
    public volatile boolean snapshotSuccess = true;
    public volatile List<KVMAgentCommands.TakeSnapshotCmd> snapshotCmds = new ArrayList<KVMAgentCommands.TakeSnapshotCmd>();
    public volatile boolean mergeSnapshotSuccess = true;
    public volatile List<MergeSnapshotCmd> mergeSnapshotCmds = new ArrayList<MergeSnapshotCmd>();
    public volatile List<AttachDataVolumeCmd> attachDataVolumeCmds = new ArrayList<AttachDataVolumeCmd>();
    public volatile List<CreateBridgeCmd> createBridgeCmds = new ArrayList<CreateBridgeCmd>();
    public volatile List<LogoutIscsiTargetCmd> logoutIscsiTargetCmds = new ArrayList<LogoutIscsiTargetCmd>();
    public volatile List<LoginIscsiTargetCmd> loginIscsiTargetCmds = new ArrayList<LoginIscsiTargetCmd>();
    public volatile List<AttachNicCommand> attachNicCommands = new ArrayList<AttachNicCommand>();
    public volatile List<DetachNicCommand> detachNicCommands = new ArrayList<DetachNicCommand>();
    public volatile boolean detachNicSuccess = true;
    public volatile List<AttachIsoCmd> attachIsoCmds = new ArrayList<AttachIsoCmd>();
    public volatile List<DetachIsoCmd> detachIsoCmds = new ArrayList<DetachIsoCmd>();
    public volatile double startVmFailureChance = 0;
    public volatile List<CheckVmStateCmd> checkVmStateCmds = new ArrayList<CheckVmStateCmd>();
    public volatile Map<String, Map<String, String>> checkVmStatesConfig = new HashMap<String, Map<String, String>>();
    public volatile boolean vmSyncSuccess = true;
    public List<HardenVmConsoleCmd> hardenVmConsoleCmds = new ArrayList<HardenVmConsoleCmd>();
    public List<DeleteVmConsoleFirewallCmd> deleteVmConsoleFirewallCmds  = new ArrayList<DeleteVmConsoleFirewallCmd>();
}
