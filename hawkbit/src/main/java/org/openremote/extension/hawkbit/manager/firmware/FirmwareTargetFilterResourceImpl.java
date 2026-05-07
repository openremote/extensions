package org.openremote.extension.hawkbit.manager.firmware;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.security.ManagerIdentityService;
import org.openremote.manager.web.ManagerWebResource;
import org.openremote.extension.hawkbit.model.firmware.FirmwareAutoAssignRequest;
import org.openremote.extension.hawkbit.model.firmware.FirmwareDistributionSet;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetFilterQueries;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetFilterQuery;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetFilterQueryRequest;
import org.openremote.extension.hawkbit.model.firmware.FirmwareTargetFilterResource;
import org.openremote.model.http.RequestParams;

public class FirmwareTargetFilterResourceImpl extends ManagerWebResource
        implements FirmwareTargetFilterResource {

    protected final FirmwareService firmwareService;

    public FirmwareTargetFilterResourceImpl(TimerService timerService, ManagerIdentityService identityService,
                                             FirmwareService firmwareService) {
        super(timerService, identityService);
        this.firmwareService = firmwareService;
    }

    @Override
    public FirmwareTargetFilterQueries getTargetFilters(RequestParams requestParams, Integer offset, Integer limit) {
        try {
            return firmwareService.targetFiltersResource.getTargetFilters(offset, limit);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware target filters", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareTargetFilterQuery getTargetFilter(RequestParams requestParams, Long id) {
        try {
            return firmwareService.targetFiltersResource.get(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to retrieve firmware target filter '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareTargetFilterQuery createTargetFilter(RequestParams requestParams,
                                                         FirmwareTargetFilterQueryRequest filter) {
        try {
            return firmwareService.targetFiltersResource.create(filter);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to create firmware target filter", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public void deleteTargetFilter(RequestParams requestParams, Long id) {
        try {
            firmwareService.targetFiltersResource.delete(id);
        } catch (Exception e) {
            throw new WebApplicationException("Failed to delete firmware target filter '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareDistributionSet getAutoAssignDS(RequestParams requestParams, Long id) {
        try {
            return firmwareService.targetFiltersResource.getAutoAssignDS(id);
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Failed to retrieve auto assign distribution set for filter '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public FirmwareTargetFilterQuery setAutoAssignDS(RequestParams requestParams, Long id,
                                                      FirmwareAutoAssignRequest request) {
        try {
            return firmwareService.targetFiltersResource.setAutoAssignDS(id, request);
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Failed to set auto assign distribution set for filter '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }

    @Override
    public void deleteAutoAssignDS(RequestParams requestParams, Long id) {
        try {
            firmwareService.targetFiltersResource.deleteAutoAssignDS(id);
        } catch (Exception e) {
            throw new WebApplicationException(
                    "Failed to remove auto assign distribution set from filter '" + id + "'", e,
                    Response.Status.BAD_GATEWAY);
        }
    }
}
