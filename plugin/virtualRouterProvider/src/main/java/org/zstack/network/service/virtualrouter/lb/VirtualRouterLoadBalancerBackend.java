package org.zstack.network.service.virtualrouter.lb;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.zstack.core.cloudbus.CloudBus;
import org.zstack.core.cloudbus.CloudBusCallBack;
import org.zstack.core.db.DatabaseFacade;
import org.zstack.core.db.SimpleQuery;
import org.zstack.core.db.SimpleQuery.Op;
import org.zstack.core.errorcode.ErrorFacade;
import org.zstack.core.workflow.FlowChainBuilder;
import org.zstack.core.workflow.ShareFlow;
import org.zstack.header.core.Completion;
import org.zstack.header.core.ReturnValueCompletion;
import org.zstack.header.core.workflow.*;
import org.zstack.header.errorcode.ErrorCode;
import org.zstack.header.errorcode.OperationFailureException;
import org.zstack.header.exception.CloudRuntimeException;
import org.zstack.header.message.MessageReply;
import org.zstack.header.network.l3.L3NetworkInventory;
import org.zstack.header.network.l3.L3NetworkVO;
import org.zstack.header.vm.DestroyVmInstanceMsg;
import org.zstack.header.vm.VmInstanceConstant;
import org.zstack.header.vm.VmNicInventory;
import org.zstack.network.service.lb.*;
import org.zstack.network.service.vip.VipInventory;
import org.zstack.network.service.vip.VipManager;
import org.zstack.network.service.vip.VipVO;
import org.zstack.network.service.virtualrouter.*;
import org.zstack.network.service.virtualrouter.vip.VirtualRouterVipBackend;
import org.zstack.utils.Utils;
import org.zstack.utils.logging.CLogger;

import javax.persistence.TypedQuery;
import java.util.List;
import java.util.Map;

import static org.zstack.utils.CollectionDSL.list;

/**
 * Created by frank on 8/9/2015.
 */
public class VirtualRouterLoadBalancerBackend implements LoadBalancerBackend {
    private static CLogger logger = Utils.getLogger(VirtualRouterLoadBalancerBackend.class);

    @Autowired
    private VirtualRouterManager vrMgr;
    @Autowired
    private DatabaseFacade dbf;
    @Autowired
    private CloudBus bus;
    @Autowired
    private VirtualRouterVipBackend vipVrBkd;
    @Autowired
    private VipManager vipMgr;
    @Autowired
    private ErrorFacade errf;

    @Transactional(readOnly = true)
    private VirtualRouterVmInventory findVirutalRouterVm(String lbUuid) {
        String sql = "select vr from VirtualRouterVmVO vr, VirtualRouterLoadBalancerRefVO ref where ref.virtualRouterVmUuid =" +
                " vr.uuid and ref.loadBalancerUuid = :lbUuid";
        TypedQuery<VirtualRouterVmVO> q = dbf.getEntityManager().createQuery(sql, VirtualRouterVmVO.class);
        q.setParameter("lbUuid", lbUuid);
        List<VirtualRouterVmVO> vrs = q.getResultList();
        return  vrs.isEmpty() ? null : VirtualRouterVmInventory.valueOf(vrs.get(0));
    }

    private void refresh(VirtualRouterVmInventory vr, LoadBalancerStruct struct, Completion completion) {

    }

    @Override
    public void addVmNic(final LoadBalancerStruct struct, VmNicInventory nic, final Completion completion) {
        VirtualRouterVmInventory vr = findVirutalRouterVm(struct.getLb().getUuid());
        if (vr != null) {
            refresh(vr, struct, completion);
            return;
        }

        final L3NetworkInventory l3 = L3NetworkInventory.valueOf(dbf.findByUuid(nic.getL3NetworkUuid(), L3NetworkVO.class));
        final VipInventory vip = VipInventory.valueOf(dbf.findByUuid(struct.getLb().getVipUuid(), VipVO.class));

        final boolean separateVr = LoadBalancerSystemTags.SEPARATE_VR.hasTag(struct.getLb().getUuid());

        final VirtualRouterVmSelector selector = separateVr ? new VirtualRouterVmSelector() {
            @Override
            public VirtualRouterVmVO select(List<VirtualRouterVmVO> vrs) {
                // always create new vr
                return null;
            }
        } : null;

        FlowChain chain = FlowChainBuilder.newShareFlowChain();
        chain.setName(String.format("add-nic-to-vr-lb-%s", struct.getLb().getUuid()));
        chain.then(new ShareFlow() {
            VirtualRouterVmInventory vr;

            @Override
            public void setup() {
                flow(new Flow() {
                    String __name__ = "lock-vip";

                    boolean success = false;

                    @Override
                    public void run(FlowTrigger trigger, Map data) {
                        vipMgr.lockVip(vip, LoadBalancerConstants.LB_NETWORK_SERVICE_TYPE_STRING);
                        success = true;
                        trigger.next();
                    }

                    @Override
                    public void rollback(FlowTrigger trigger, Map data) {
                        if (success) {
                            vipMgr.unlockVip(vip);
                        }
                        trigger.rollback();
                    }
                });

                if (separateVr) {
                    flow(new Flow() {
                        String __name__ = "create-separate-vr";

                        @Override
                        public void run(final FlowTrigger trigger, Map data) {
                            vrMgr.acquireVirtualRouterVm(l3, null, selector, new ReturnValueCompletion<VirtualRouterVmInventory>(trigger) {
                                @Override
                                public void success(VirtualRouterVmInventory returnValue) {
                                    vr = returnValue;
                                    new VirtualRouterRoleManager().makeLoadBalancerRole(vr.getUuid());
                                    trigger.next();
                                }

                                @Override
                                public void fail(ErrorCode errorCode) {
                                    trigger.fail(errorCode);
                                }
                            });
                        }

                        @Override
                        public void rollback(final FlowTrigger trigger, Map data) {
                            DestroyVmInstanceMsg msg = new DestroyVmInstanceMsg();
                            msg.setVmInstanceUuid(vr.getUuid());
                            bus.makeTargetServiceIdByResourceUuid(msg, VmInstanceConstant.SERVICE_ID, vr.getUuid());
                            bus.send(msg, new CloudBusCallBack(trigger) {
                                @Override
                                public void run(MessageReply reply) {
                                    if (!reply.isSuccess()) {
                                        //TODO:
                                        logger.warn(String.format("failed to destroy vr[uuid:%s], %s. Need a cleanup", vr.getUuid(), reply.getError()));
                                    }

                                    trigger.rollback();
                                }
                            });
                        }
                    });
                } else {
                    flow(new NoRollbackFlow() {
                        String __name__ = "acquire-vr";

                        @Override
                        public void run(final FlowTrigger trigger, Map data) {
                            vrMgr.acquireVirtualRouterVm(l3, (VirtualRouterOfferingValidator)null, new ReturnValueCompletion<VirtualRouterVmInventory>(trigger) {
                                @Override
                                public void success(VirtualRouterVmInventory returnValue) {
                                    vr = returnValue;
                                    new VirtualRouterRoleManager().makeLoadBalancerRole(vr.getUuid());
                                    trigger.next();
                                }

                                @Override
                                public void fail(ErrorCode errorCode) {
                                    trigger.fail(errorCode);
                                }
                            });
                        }
                    });
                }

                flow(new Flow() {
                    String __name__ = "create-vip-on-vr";
                    boolean success = false;

                    @Override
                    public void run(final FlowTrigger trigger, Map data) {
                        vipVrBkd.createVipOnVirtualRouterVm(vr, list(vip), new Completion(trigger) {
                            @Override
                            public void success() {
                                success = true;
                                trigger.next();
                            }

                            @Override
                            public void fail(ErrorCode errorCode) {
                                trigger.fail(errorCode);
                            }
                        });
                    }

                    @Override
                    public void rollback(final FlowTrigger trigger, Map data) {
                        if (success) {
                            vipVrBkd.releaseVipOnVirtualRouterVm(vr, vip, new Completion(trigger) {
                                @Override
                                public void success() {
                                    trigger.rollback();
                                }

                                @Override
                                public void fail(ErrorCode errorCode) {
                                    logger.warn(String.format("failed to release vip[uuid:%s, ip:%s] on vr[uuid:%s], continue to rollback",
                                            vip.getUuid(), vip.getIp(), vr.getUuid()));
                                    trigger.rollback();
                                }
                            });
                        } else {
                            trigger.rollback();
                        }
                    }
                });

                flow(new NoRollbackFlow() {
                    @Override
                    public void run(final FlowTrigger trigger, Map data) {
                        refresh(vr, struct, new Completion(trigger) {
                            @Override
                            public void success() {
                                trigger.next();
                            }

                            @Override
                            public void fail(ErrorCode errorCode) {
                                trigger.fail(errorCode);
                            }
                        });
                    }
                });

                done(new FlowDoneHandler(completion) {
                    @Override
                    public void handle(Map data) {
                        VirtualRouterLoadBalancerRefVO ref = new VirtualRouterLoadBalancerRefVO();
                        ref.setLoadBalancerUuid(struct.getLb().getUuid());
                        ref.setVirtualRouterVmUuid(vr.getUuid());
                        dbf.persist(ref);

                        completion.success();
                    }
                });

                error(new FlowErrorHandler(completion) {
                    @Override
                    public void handle(ErrorCode errCode, Map data) {
                        completion.fail(errCode);
                    }
                });
            }
        }).start();
    }

    @Override
    public void removeVmNic(LoadBalancerStruct struct, VmNicInventory nic, Completion completion) {
        VirtualRouterVmInventory vr = findVirutalRouterVm(struct.getLb().getUuid());
        if (vr == null) {
            // the vr has been destroyed
            completion.success();
            return;
        }

        refresh(vr, struct, completion);
    }

    @Override
    public void addListener(LoadBalancerStruct struct, LoadBalancerListenerInventory listener, Completion completion) {
        VirtualRouterVmInventory vr = findVirutalRouterVm(struct.getLb().getUuid());
        if (vr == null) {
            throw new OperationFailureException(errf.stringToOperationError(
                    String.format("cannot find virtual router for load balancer [uuid:%s]", struct.getLb().getUuid())
            ));
        }

        refresh(vr, struct, completion);
    }

    @Override
    public void removeListener(LoadBalancerStruct struct, LoadBalancerListenerInventory listener, Completion completion) {
        VirtualRouterVmInventory vr = findVirutalRouterVm(struct.getLb().getUuid());
        if (vr == null) {
            // the vr has been destroyed
            completion.success();
            return;
        }

        refresh(vr, struct, completion);
    }

    @Override
    public void destroy(LoadBalancerStruct struct, final Completion completion) {
        VirtualRouterVmInventory vr = findVirutalRouterVm(struct.getLb().getUuid());
        if (vr == null) {
            // the vr has been destroyed
            completion.success();
            return;
        }

        SimpleQuery<VirtualRouterLoadBalancerRefVO> q = dbf.createQuery(VirtualRouterLoadBalancerRefVO.class);
        q.add(VirtualRouterLoadBalancerRefVO_.loadBalancerUuid, Op.EQ, struct.getLb().getUuid());
        q.add(VirtualRouterLoadBalancerRefVO_.virtualRouterVmUuid, Op.EQ, vr.getUuid());
        final VirtualRouterLoadBalancerRefVO ref = q.find();

        List<String> roles = new VirtualRouterRoleManager().getAllRoles(vr.getUuid());
        if (roles.size() == 1 && roles.contains(VirtualRouterSystemTags.VR_LB_ROLE.getTagFormat())) {
            DestroyVmInstanceMsg msg = new DestroyVmInstanceMsg();
            msg.setVmInstanceUuid(vr.getUuid());
            bus.makeTargetServiceIdByResourceUuid(msg, VmInstanceConstant.SERVICE_ID, vr.getUuid());
            bus.send(msg, new CloudBusCallBack(completion) {
                @Override
                public void run(MessageReply reply) {
                    if (reply.isSuccess()) {
                        dbf.remove(ref);
                        completion.success();
                    } else {
                        completion.fail(reply.getError());
                    }
                }
            });
        } else if (roles.size() > 1 && roles.contains(VirtualRouterSystemTags.VR_LB_ROLE.getTagFormat())) {
            refresh(vr, struct, new Completion(completion) {
                @Override
                public void success() {
                    dbf.remove(ref);
                    completion.success();
                }

                @Override
                public void fail(ErrorCode errorCode) {
                    completion.fail(errorCode);
                }
            });
        } else {
            throw new CloudRuntimeException(String.format("wrong virtual router roles%s. it doesn't have the role[%s]",
                    roles, VirtualRouterSystemTags.VR_LB_ROLE.getTagFormat()));
        }
    }

    @Override
    public String getNetworkServiceProviderType() {
        return VirtualRouterConstant.VIRTUAL_ROUTER_PROVIDER_TYPE;
    }
}