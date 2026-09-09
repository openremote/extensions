/*
 * Copyright 2025, OpenRemote Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as
 * published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program. If not, see <https://www.gnu.org/licenses/>.
 *
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package org.openremote.extension.ems.manager;

import java.util.concurrent.ScheduledExecutorService;
import org.openremote.container.message.MessageBrokerService;
import org.openremote.container.timer.TimerService;
import org.openremote.manager.asset.AssetProcessingService;
import org.openremote.manager.asset.AssetStorageService;
import org.openremote.manager.datapoint.AssetDatapointService;
import org.openremote.manager.datapoint.AssetPredictedDatapointService;
import org.openremote.manager.event.ClientEventService;
import org.openremote.manager.gateway.GatewayService;

public class Services {
  private final AssetDatapointService assetDatapointService;
  private final AssetPredictedDatapointService assetPredictedDatapointService;
  private final AssetProcessingService assetProcessingService;
  private final AssetStorageService assetStorageService;
  private final ClientEventService clientEventService;
  private final GatewayService gatewayService;
  private final MessageBrokerService messageBrokerService;
  private final ScheduledExecutorService scheduledExecutorService;
  private final TimerService timerService;

  private Services(Builder builder) {
    this.assetDatapointService = builder.assetDatapointService;
    this.assetPredictedDatapointService = builder.assetPredictedDatapointService;
    this.assetProcessingService = builder.assetProcessingService;
    this.assetStorageService = builder.assetStorageService;
    this.clientEventService = builder.clientEventService;
    this.gatewayService = builder.gatewayService;
    this.messageBrokerService = builder.messageBrokerService;
    this.scheduledExecutorService = builder.scheduledExecutorService;
    this.timerService = builder.timerService;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {
    private AssetDatapointService assetDatapointService;
    private AssetPredictedDatapointService assetPredictedDatapointService;
    private AssetProcessingService assetProcessingService;
    private AssetStorageService assetStorageService;
    private ClientEventService clientEventService;
    private GatewayService gatewayService;
    private MessageBrokerService messageBrokerService;
    private ScheduledExecutorService scheduledExecutorService;
    private TimerService timerService;

    public Builder withAssetDatapointService(AssetDatapointService service) {
      this.assetDatapointService = service;
      return this;
    }

    public Builder withAssetPredictedDatapointService(AssetPredictedDatapointService service) {
      this.assetPredictedDatapointService = service;
      return this;
    }

    public Builder withAssetProcessingService(AssetProcessingService service) {
      this.assetProcessingService = service;
      return this;
    }

    public Builder withAssetStorageService(AssetStorageService service) {
      this.assetStorageService = service;
      return this;
    }

    public Builder withClientEventService(ClientEventService service) {
      this.clientEventService = service;
      return this;
    }

    public Builder withGatewayService(GatewayService service) {
      this.gatewayService = service;
      return this;
    }

    public Builder withMessageBrokerService(MessageBrokerService service) {
      this.messageBrokerService = service;
      return this;
    }

    public Builder withScheduledExecutorService(ScheduledExecutorService service) {
      this.scheduledExecutorService = service;
      return this;
    }

    public Builder withTimerService(TimerService service) {
      this.timerService = service;
      return this;
    }

    public Services build() {
      return new Services(this);
    }
  }

  // Getters for each service
  public AssetDatapointService getAssetDatapointService() {
    return assetDatapointService;
  }

  public AssetPredictedDatapointService getAssetPredictedDatapointService() {
    return assetPredictedDatapointService;
  }

  public AssetProcessingService getAssetProcessingService() {
    return assetProcessingService;
  }

  public AssetStorageService getAssetStorageService() {
    return assetStorageService;
  }

  public ClientEventService getClientEventService() {
    return clientEventService;
  }

  public GatewayService getGatewayService() {
    return gatewayService;
  }

  public MessageBrokerService getMessageBrokerService() {
    return messageBrokerService;
  }

  public ScheduledExecutorService getScheduledExecutorService() {
    return scheduledExecutorService;
  }

  public TimerService getTimerService() {
    return timerService;
  }
}
