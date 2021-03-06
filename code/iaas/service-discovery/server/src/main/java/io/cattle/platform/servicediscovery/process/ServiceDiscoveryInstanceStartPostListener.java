package io.cattle.platform.servicediscovery.process;

import static io.cattle.platform.core.model.tables.ServiceExposeMapTable.SERVICE_EXPOSE_MAP;
import io.cattle.platform.core.constants.InstanceConstants;
import io.cattle.platform.core.dao.GenericMapDao;
import io.cattle.platform.core.dao.GenericResourceDao;
import io.cattle.platform.core.model.Instance;
import io.cattle.platform.core.model.LoadBalancer;
import io.cattle.platform.core.model.Service;
import io.cattle.platform.core.model.ServiceExposeMap;
import io.cattle.platform.engine.handler.HandlerResult;
import io.cattle.platform.engine.handler.ProcessPostListener;
import io.cattle.platform.engine.process.ProcessInstance;
import io.cattle.platform.engine.process.ProcessState;
import io.cattle.platform.lb.instance.service.LoadBalancerInstanceManager;
import io.cattle.platform.object.process.StandardProcess;
import io.cattle.platform.process.common.handler.AbstractObjectProcessLogic;
import io.cattle.platform.util.type.Priority;

import java.util.List;

import javax.inject.Inject;
import javax.inject.Named;

/**
 * this handler registers service discovery instance in service_expose maps
 *
 */

@Named
public class ServiceDiscoveryInstanceStartPostListener extends AbstractObjectProcessLogic implements
        ProcessPostListener,
        Priority {

    @Inject
    GenericMapDao mapDao;

    @Inject
    GenericResourceDao resourceDao;

    @Inject
    LoadBalancerInstanceManager lbInstanceService;


    @Override
    public String[] getProcessNames() {
        return new String[] { InstanceConstants.PROCESS_START };
    }

    @Override
    public HandlerResult handle(ProcessState state, ProcessInstance process) {
        Instance instance = (Instance) state.getResource();
        if (lbInstanceService.isLbInstance(instance)) {
            LoadBalancer lb = lbInstanceService.getLoadBalancerForInstance(instance);
            Long serviceId = lb.getServiceId();
            if (serviceId != null) {
                // for the lb instance, service map gets created only at this point
                // as the instance gets created in generic manner by AgentBuilder factory, and I avoided to make service
                // specific modifications there
                ServiceExposeMap instanceServiceMap = mapDao.findNonRemoved(ServiceExposeMap.class, Instance.class,
                        instance.getId(),
                        Service.class, serviceId);

                if (instanceServiceMap == null) {
                    instanceServiceMap = resourceDao.createAndSchedule(ServiceExposeMap.class,
                            SERVICE_EXPOSE_MAP.INSTANCE_ID,
                            instance.getId(), SERVICE_EXPOSE_MAP.SERVICE_ID, serviceId);
                }
            }
        } else {
            // for regular, non lb instance, the instance->service map gets created as a part of the instance creation
            // (within the same transaction), so it should exist at this point
            List<? extends ServiceExposeMap> instanceServiceMap = mapDao.findNonRemoved(ServiceExposeMap.class,
                    Instance.class,
                    instance.getId());
            if (instanceServiceMap.isEmpty()) {
                // not a service instance
                return null;
            }
            objectProcessManager.scheduleStandardProcess(StandardProcess.CREATE, instanceServiceMap.get(0), null);
        }
        return null;
    }

    @Override
    public int getPriority() {
        return Priority.DEFAULT_OVERRIDE;
    }

}
