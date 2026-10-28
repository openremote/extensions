/*
 * Copyright 2026, OpenRemote Inc.
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
package org.openremote.extension.ems.manager.distroenergy

import jakarta.ws.rs.NotFoundException
import jakarta.ws.rs.ProcessingException
import jakarta.ws.rs.ServiceUnavailableException
import jakarta.ws.rs.WebApplicationException
import org.openremote.container.timer.TimerService
import org.openremote.extension.ems.agent.EmsDistroEnergyAsset
import org.openremote.extension.ems.agent.EmsElectricityBatteryAsset
import org.openremote.extension.ems.agent.EmsElectricityBatteryAsset.EmsElectricityBatteryConnectionStatusValueType
import org.openremote.extension.ems.manager.distroenergy.dto.GopacsOrder
import org.openremote.extension.ems.manager.distroenergy.dto.GopacsOrderRequest
import org.openremote.extension.ems.manager.distroenergy.dto.GopacsSettlement
import org.openremote.extension.ems.manager.distroenergy.dto.GopacsSettlementPage
import org.openremote.manager.asset.AssetProcessingService
import org.openremote.manager.asset.AssetStorageService
import org.openremote.model.Container
import org.openremote.model.attribute.AttributeEvent
import org.openremote.model.util.ValueUtil
import org.openremote.model.value.AttributeDescriptor
import spock.lang.Specification

import java.time.Instant
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture

import static org.openremote.extension.ems.manager.distroenergy.DistroGopacsHandler.BUY
import static org.openremote.extension.ems.manager.distroenergy.DistroGopacsHandler.SELL

/**
 * Covers the order sizing rules and the attribute-driven order lifecycle of {@link
 * DistroGopacsHandler}. The Distro API is a Spock mock of {@link DistroGopacsResource} handed to the
 * test-support constructor, and the executor runs submitted work inline, so every reaction happens
 * on the test thread.
 */
class DistroGopacsHandlerTest extends Specification {

  static final String ASSET_ID = "distroAsset1"
  static final String PARENT_ID = "optimisationAsset1"
  static final String TENANT = "slimstrandnet"
  static final String EAN = "871685920001768800"
  static final String ANNOUNCEMENT = "4f1c2b3a-0000-4000-8000-000000000001"
  static final long NOW = Instant.parse("2026-10-22T10:00:00Z").toEpochMilli()
  static final long START = Instant.parse("2026-10-22T14:00:00Z").toEpochMilli()
  static final long END = Instant.parse("2026-10-22T16:00:00Z").toEpochMilli()

  DistroGopacsResource resource
  EmsDistroEnergyAsset asset
  List<EmsElectricityBatteryAsset> batteries
  List<AttributeEvent> sent
  DistroGopacsHandler handler

  def setupSpec() {
    // Asset construction needs the attribute descriptors of this extension's asset types.
    ValueUtil.initialise(null)
  }

  def setup() {
    resource = Mock(DistroGopacsResource)
    asset = new EmsDistroEnergyAsset("distro").setId(ASSET_ID).setParentId(PARENT_ID)
    batteries = [battery()]
    sent = []

    def assetProcessingService = Stub(AssetProcessingService) {
      sendAttributeEvent(_, _) >> { AttributeEvent event, String source -> sent << event }
    }
    def assetStorageService = Stub(AssetStorageService) {
      find(ASSET_ID) >> { asset }
      findAll(_) >> { batteries }
    }
    def timerService = Stub(TimerService) {
      getCurrentTimeMillis() >> NOW
      getNow() >> Instant.ofEpochMilli(NOW)
    }
    def executor = Stub(ScheduledExecutorService) {
      execute(_) >> { Runnable task -> task.run() }
      scheduleAtFixedRate(*_) >> Stub(ScheduledFuture)
    }
    def container = Stub(Container) {
      getConfig() >> [(DistroGopacsHandler.DISTRO_GOPACS_TENANT_ID): TENANT]
      getService(TimerService) >> timerService
      getService(AssetStorageService) >> assetStorageService
      getService(AssetProcessingService) >> assetProcessingService
      getScheduledExecutor() >> executor
    }
    handler = new DistroGopacsHandler(ASSET_ID, container, resource)
  }

  // --- sizing rules ---

  def "category #category with buy #buyKw kW and sell #sellKw kW favours #expected"() {
    expect:
    DistroGopacsHandler.orderTypeFor(category, buyKw, sellKw) == expected

    where:
    category                      | buyKw | sellKw || expected
    "BUY_MAXIMUM_SELL_NONE"       | 0     | 500    || BUY
    "BUY_VERY_HIGH_SELL_VERY_LOW" | 0     | 500    || BUY
    "BUY_HIGH_SELL_LOW"           | 0     | 500    || BUY
    "BUY_LOW_SELL_HIGH"           | 500   | 0      || SELL
    "BUY_VERY_LOW_SELL_VERY_HIGH" | 500   | 0      || SELL
    "BUY_NONE_SELL_MAXIMUM"       | 500   | 0      || SELL
    "BUY_MODERATE_SELL_MODERATE"  | 300   | 200    || BUY
    "BUY_MODERATE_SELL_MODERATE"  | 200   | 300    || SELL
    "BUY_MODERATE_SELL_MODERATE"  | 200   | 200    || BUY
    "BUY_NONE_SELL_NONE"          | 500   | 500    || null
    "THREE_PHASE_NETWORK_REDUCE"  | 500   | 500    || null
    "BUY_SOME_SELL_NONE"          | 500   | 500    || null
    null                          | 500   | 500    || null
  }

  def "quantity is the battery power the state of charge can hold for the window: #label"() {
    expect:
    DistroGopacsHandler.quantityKw(fleet, orderType, hours) == expectedKw

    where:
    label                                | fleet                                                    | orderType | hours || expectedKw
    "BUY limited by energy headroom"     | [battery()]                                              | BUY       | 2     || 200
    "BUY limited by power"               | [battery()]                                              | BUY       | 0.5   || 500
    "SELL uses the negative discharge"   | [battery()]                                              | SELL      | 0.5   || 400
    "SELL limited by energy headroom"    | [battery()]                                              | SELL      | 2     || 200
    "charge efficiency raises grid energy" | [battery(chargeEfficiency: 80)]                        | BUY       | 2     || 200
    "discharge efficiency lowers it"     | [battery(dischargeEfficiency: 50)]                       | SELL      | 2     || 100
    "batteries add up"                   | [battery(), battery()]                                   | BUY       | 2     || 400
    "rounded down to 100 kW steps"       | [battery(level: 45d)]                                     | SELL      | 2     || 100
    "charging not allowed"               | [battery(allowCharging: false)]                          | BUY       | 2     || 0
    "disconnected"                       | [battery(connection: EmsElectricityBatteryConnectionStatusValueType.disconnected)] | BUY | 2 || 0
    "already full"                       | [battery(level: 90d)]                                     | BUY       | 2     || 0
    "no state of charge"                 | [battery(level: null)]                                   | BUY       | 2     || 0
    "empty window"                       | [battery()]                                              | BUY       | 0     || 0
  }

  def "expiration #label"() {
    expect:
    DistroGopacsHandler.expirationSeconds(seconds(START), validityEnd, seconds(NOW)) == expected

    where:
    label                                     | validityEnd                           || expected
    "is the bid validity end on a quarter"    | seconds("2026-10-22T12:00:00Z")       || seconds("2026-10-22T12:00:00Z")
    "is rounded down to a quarter-hour"       | seconds("2026-10-22T12:07:00Z")       || seconds("2026-10-22T12:00:00Z")
    "is left to Distro when unknown"          | null                                  || null
    "may be 15 minutes before start"          | seconds("2026-10-22T13:50:00Z")       || seconds("2026-10-22T13:45:00Z")
    "is left to Distro when after its default" | seconds("2026-10-22T14:10:00Z")      || null
    "is left to Distro when already passed"   | seconds("2026-10-22T09:00:00Z")       || null
  }

  // --- suggestion ---

  def "a complete announcement pre-fills order type and quantity"() {
    given:
    announcement("BUY_LOW_SELL_HIGH")

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_EAN_EFFECTIVITY.name, null, "BUY_LOW_SELL_HIGH")

    then:
    sentValue(EmsDistroEnergyAsset.GOPACS_ORDER_TYPE) == SELL
    sentValue(EmsDistroEnergyAsset.GOPACS_ORDER_QUANTITY) == 200d
  }

  def "an announcement neither side helps clears the suggestion"() {
    given:
    announcement("BUY_NONE_SELL_NONE")

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_EAN_EFFECTIVITY.name, null, "BUY_NONE_SELL_NONE")

    then:
    sent.find { it.name == EmsDistroEnergyAsset.GOPACS_ORDER_TYPE.name }.value.isEmpty()
    sent.find { it.name == EmsDistroEnergyAsset.GOPACS_ORDER_QUANTITY.name }.value.isEmpty()
  }

  def "batteries that cannot hold the 100 kW minimum clear the suggestion"() {
    given:
    announcement("BUY_LOW_SELL_HIGH")
    batteries = [battery(level: 12d)]

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_EAN_EFFECTIVITY.name, null, "BUY_LOW_SELL_HIGH")

    then:
    sent.find { it.name == EmsDistroEnergyAsset.GOPACS_ORDER_TYPE.name }.value.isEmpty()
    sent.find { it.name == EmsDistroEnergyAsset.GOPACS_ORDER_QUANTITY.name }.value.isEmpty()
  }

  def "an unchanged republish of an attribute does nothing"() {
    given:
    announcement("BUY_LOW_SELL_HIGH")
    def attribute = asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID)

    when:
    handler.onAttributeEvent(new AttributeEvent(asset, attribute, "AttributeLinkingService",
            ANNOUNCEMENT, NOW, ANNOUNCEMENT, NOW - 1))

    then:
    sent.isEmpty()
    0 * resource._
  }

  // --- order lifecycle ---

  def "confirming posts the order in MW and epoch seconds and tracks it"() {
    given:
    announcement("BUY_LOW_SELL_HIGH")
    order(SELL, 200, 45)
    GopacsOrderRequest request = null

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER.name, false, true)

    then:
    1 * resource.createOrder(TENANT, _) >> { String tenant, GopacsOrderRequest body ->
      request = body
      return stored(body.reference(), "PENDING", ANNOUNCEMENT)
    }
    request.reference() ==~ /[a-z0-9-]{36}/
    request.orderType() == SELL
    request.quantity() == 0.2d
    request.start() == seconds(START)
    request.end() == seconds(END)
    request.price() == 45d
    request.ean() == EAN
    request.metadata() == [announcementId: ANNOUNCEMENT]
    request.customExpirationTime() == seconds("2026-10-22T12:00:00Z")
    handler.orders.keySet() == [request.reference()] as Set
    (sentValue(EmsDistroEnergyAsset.GOPACS_ORDERS) as Map).containsKey(request.reference())
    (sentValue(EmsDistroEnergyAsset.GOPACS_ORDER_HISTORY) as Map).action == "CREATED"
  }

  def "confirming rounds the quantity down to 100 kW steps and the price to cents"() {
    given:
    announcement("BUY_LOW_SELL_HIGH")
    order(SELL, 250, 45.678)

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER.name, false, true)

    then:
    1 * resource.createOrder(TENANT, { it.quantity() == 0.2d && it.price() == 45.68d }) >> { String tenant, GopacsOrderRequest body ->
      stored(body.reference(), "SUBMITTED", ANNOUNCEMENT)
    }
  }

  def "confirming refuses what Distro would reject: #label"() {
    given:
    announcement("BUY_LOW_SELL_HIGH")
    order(SELL, quantityKw, price)
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_START_TIME).setValue(start)

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER.name, false, true)

    then:
    0 * resource.createOrder(*_)
    (sentValue(EmsDistroEnergyAsset.GOPACS_ORDER_HISTORY) as Map).detail == detail

    where:
    label                       | quantityKw | price | start                                            || detail
    "a quantity rounded to 0"   | 99         | 45    | START                                            || "quantity 99.0 kW is below the 100 kW minimum"
    "a negative price"          | 200        | -1    | START                                            || "price -1.0 EUR/MWh is negative"
    "less than 15 minutes left" | 200        | 45    | Instant.parse("2026-10-22T10:15:00Z").toEpochMilli() || "window starts in less than 15 minutes"
  }

  def "a failed create is recorded and nothing is tracked"() {
    given:
    announcement("BUY_LOW_SELL_HIGH")
    order(SELL, 200, 45)

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER.name, false, true)

    then:
    1 * resource.createOrder(TENANT, _) >> { throw new WebApplicationException(403) }
    handler.orders.isEmpty()
    (sentValue(EmsDistroEnergyAsset.GOPACS_ORDER_HISTORY) as Map).detail == "HTTP 403"
  }

  def "a create that times out stays tracked as pending so polling can find it"() {
    given:
    announcement("BUY_LOW_SELL_HIGH")
    order(SELL, 200, 45)

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER.name, false, true)

    then:
    1 * resource.createOrder(TENANT, _) >> { throw new ProcessingException("Read timed out") }
    handler.orders.size() == 1
    handler.orders.values().first().status() == "PENDING"
    (sentValue(EmsDistroEnergyAsset.GOPACS_ORDERS) as Map).size() == 1
    (sentValue(EmsDistroEnergyAsset.GOPACS_ORDER_HISTORY) as Map).action == "CREATE_FAILED"
  }

  def "a price change replaces the submitted orders of the current announcement only"() {
    given:
    announcement("BUY_LOW_SELL_HIGH")
    track(stored("open-current", "SUBMITTED", ANNOUNCEMENT))
    track(stored("pending-current", "PENDING", ANNOUNCEMENT))
    track(stored("locked-current", "LOCKED", ANNOUNCEMENT))
    track(stored("open-other", "SUBMITTED", "other-announcement"))

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_ORDER_PRICE.name, 45d, 60.004d)

    then:
    1 * resource.replaceOrder(TENANT, "open-current", { it.price() == 60d && it.reference() == "open-current" }) >>
            stored("open-current", "SUBMITTED", ANNOUNCEMENT, 60)
    0 * resource.replaceOrder(*_)
    handler.orders["open-current"].price() == 60d
  }

  def "a negative price is refused without replacing anything"() {
    given:
    announcement("BUY_LOW_SELL_HIGH")
    track(stored("open-current", "SUBMITTED", ANNOUNCEMENT))

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_ORDER_PRICE.name, 45d, -5d)

    then:
    0 * resource._
    (sentValue(EmsDistroEnergyAsset.GOPACS_ORDER_HISTORY) as Map).action == "REFUSED"
  }

  def "a new announcement cancels the open orders placed for the previous one"() {
    given:
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID).setValue("next")
    track(stored("open-previous", "PENDING", ANNOUNCEMENT))
    track(stored("matched-previous", "MATCHED", ANNOUNCEMENT))
    track(stored("open-next", "PENDING", "next"))

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID.name, ANNOUNCEMENT, "next")

    then:
    1 * resource.cancelOrder(TENANT, "open-previous")
    0 * resource.cancelOrder(*_)
    handler.orders.keySet() == ["matched-previous", "open-next"] as Set
  }

  def "the cancel flag cancels every open order and is reset"() {
    given:
    track(stored("a", "PENDING", ANNOUNCEMENT))
    track(stored("b", "SUBMITTED", "other-announcement"))
    track(stored("c", "LOCKED", ANNOUNCEMENT))

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_CANCEL_ORDERS.name, false, true)

    then:
    1 * resource.cancelOrder(TENANT, "a")
    1 * resource.cancelOrder(TENANT, "b")
    0 * resource.cancelOrder(*_)
    handler.orders.keySet() == ["c"] as Set
  }

  def "polling keeps live orders, records settled trades and drops final orders"() {
    given:
    track(stored("live", "SUBMITTED", ANNOUNCEMENT))
    track(stored("settled", "MATCHED", ANNOUNCEMENT))
    track(stored("gone", "PENDING", ANNOUNCEMENT))
    def trade = new GopacsSettlement("trade-1", "settled", BUY, seconds(START), seconds(END),
            0.2, 0.4, 45, 18, seconds(NOW), seconds(NOW))

    when:
    handler.pollOrders()

    then:
    1 * resource.getOrder(TENANT, "live") >> stored("live", "MATCHED", ANNOUNCEMENT)
    1 * resource.getOrder(TENANT, "settled") >> stored("settled", "SETTLED", ANNOUNCEMENT)
    1 * resource.getOrder(TENANT, "gone") >> { throw new NotFoundException() }
    1 * resource.listSettlements(TENANT, seconds(START), seconds(END), "settled", 100, 0) >>
            new GopacsSettlementPage([trade], 1)
    handler.orders.keySet() == ["live"] as Set
    handler.orders["live"].status() == "MATCHED"
    sent.findAll { it.name == EmsDistroEnergyAsset.GOPACS_SETTLEMENT_HISTORY.name }*.value*.get()*.tradeId == ["trade-1"]
    sentValue(EmsDistroEnergyAsset.GOPACS_LAST_SYNC) == NOW
  }

  def "a failed settlement fetch keeps the order and is retried without repeating its status"() {
    given:
    track(stored("settled", "MATCHED", ANNOUNCEMENT))
    def trade = new GopacsSettlement("trade-1", "settled", BUY, seconds(START), seconds(END),
            0.2, 0.4, 45, 18, seconds(NOW), seconds(NOW))

    when:
    handler.pollOrders()

    then:
    1 * resource.getOrder(TENANT, "settled") >> stored("settled", "SETTLED", ANNOUNCEMENT)
    1 * resource.listSettlements(*_) >> { throw new ServiceUnavailableException() }
    handler.orders["settled"].status() == "SETTLED"

    when:
    sent.clear()
    handler.pollOrders()

    then:
    1 * resource.getOrder(TENANT, "settled") >> stored("settled", "SETTLED", ANNOUNCEMENT)
    1 * resource.listSettlements(*_) >> new GopacsSettlementPage([trade], 1)
    handler.orders.isEmpty()
    sent.findAll { it.name == EmsDistroEnergyAsset.GOPACS_ORDER_HISTORY.name }.isEmpty()
    sent.findAll { it.name == EmsDistroEnergyAsset.GOPACS_SETTLEMENT_HISTORY.name }.size() == 1
  }

  def "settlements are read page by page until the total is reached"() {
    given:
    track(stored("settled", "MATCHED", ANNOUNCEMENT))
    def trade = { String id -> new GopacsSettlement(id, "settled", BUY, seconds(START), seconds(END),
            0.1, 0.2, 45, 9, seconds(NOW), seconds(NOW)) }

    when:
    handler.pollOrders()

    then:
    1 * resource.getOrder(TENANT, "settled") >> stored("settled", "SETTLED", ANNOUNCEMENT)
    1 * resource.listSettlements(TENANT, seconds(START), seconds(END), "settled", 100, 0) >>
            new GopacsSettlementPage((1..100).collect { trade("t-$it") }, 101)
    1 * resource.listSettlements(TENANT, seconds(START), seconds(END), "settled", 100, 100) >>
            new GopacsSettlementPage([trade("t-101")], 101)
    sent.findAll { it.name == EmsDistroEnergyAsset.GOPACS_SETTLEMENT_HISTORY.name }.size() == 101
  }

  def "an undeployed handler ignores queued work"() {
    given:
    announcement("BUY_LOW_SELL_HIGH")
    order(SELL, 200, 45)
    track(stored("live", "SUBMITTED", ANNOUNCEMENT))
    handler.undeploy(false)

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER.name, false, true)
    handler.pollOrders()

    then:
    0 * resource._
    sent.isEmpty()
  }

  def "undeploying for good cancels the open orders and keeps the rest"() {
    given:
    track(stored("pending", "PENDING", ANNOUNCEMENT))
    track(stored("submitted", "SUBMITTED", "other-announcement"))
    track(stored("matched", "MATCHED", ANNOUNCEMENT))

    when:
    handler.undeploy(true)

    then:
    1 * resource.cancelOrder(TENANT, "pending")
    1 * resource.cancelOrder(TENANT, "submitted")
    0 * resource._
    handler.orders.keySet() == ["matched"] as Set
  }

  def "deploy restores the orders saved on the asset"() {
    given:
    def saved = stored("saved", "SUBMITTED", ANNOUNCEMENT)
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_ORDERS)
            .setValue(ValueUtil.JSON.convertValue([saved: saved], org.openremote.model.value.ValueType.ObjectMap))

    when:
    handler.deploy()

    then:
    handler.orders == [saved: saved]
  }

  // --- helpers ---

  static long seconds(long millis) {
    return millis.intdiv(1000)
  }

  static long seconds(String instant) {
    return Instant.parse(instant).epochSecond
  }

  static EmsElectricityBatteryAsset battery(Map overrides = [:]) {
    Map values = [allowCharging       : true, allowDischarging: true, capacity: 1000d, level: 50d,
                  max                 : 90, min: 10, chargeMax: 500d, dischargeMax: -400d,
                  chargeEfficiency    : 100, dischargeEfficiency: 100,
                  connection          : EmsElectricityBatteryConnectionStatusValueType.connected] + overrides
    def battery = new EmsElectricityBatteryAsset("battery")
    def attributes = battery.attributes
    attributes.getOrCreate(EmsElectricityBatteryAsset.ALLOW_CHARGING).setValue(values.allowCharging)
    attributes.getOrCreate(EmsElectricityBatteryAsset.ALLOW_DISCHARGING).setValue(values.allowDischarging)
    attributes.getOrCreate(EmsElectricityBatteryAsset.ENERGY_CAPACITY).setValue(values.capacity)
    attributes.getOrCreate(EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE).setValue(values.level)
    attributes.getOrCreate(EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE_MAXIMUM).setValue(values.max)
    attributes.getOrCreate(EmsElectricityBatteryAsset.ENERGY_LEVEL_PERCENTAGE_MINIMUM).setValue(values.min)
    attributes.getOrCreate(EmsElectricityBatteryAsset.CHARGE_POWER_MAXIMUM).setValue(values.chargeMax)
    attributes.getOrCreate(EmsElectricityBatteryAsset.DISCHARGE_POWER_MAXIMUM).setValue(values.dischargeMax)
    attributes.getOrCreate(EmsElectricityBatteryAsset.CHARGE_EFFICIENCY).setValue(values.chargeEfficiency)
    attributes.getOrCreate(EmsElectricityBatteryAsset.DISCHARGE_EFFICIENCY).setValue(values.dischargeEfficiency)
    attributes.getOrCreate(EmsElectricityBatteryAsset.CONNECTION_STATUS).setValue(values.connection)
    return battery
  }

  void announcement(String category) {
    def attributes = asset.attributes
    attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_EAN).setValue(EAN)
    attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID).setValue(ANNOUNCEMENT)
    attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_START_TIME).setValue(START)
    attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_END_TIME).setValue(END)
    attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_BID_VALIDITY_END)
            .setValue(Instant.parse("2026-10-22T12:07:00Z").toEpochMilli())
    attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_EAN_EFFECTIVITY).setValue(category)
  }

  void order(String orderType, double quantityKw, double price) {
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_ORDER_TYPE).setValue(orderType)
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_ORDER_QUANTITY).setValue(quantityKw)
    asset.setGopacsOrderPrice(price)
  }

  static GopacsOrder stored(String reference, String status, String announcementId, double price = 45) {
    return new GopacsOrder(reference, null, status, SELL, 0.2, price, seconds(START), seconds(END),
            EAN, [announcementId: announcementId], null, null)
  }

  void track(GopacsOrder order) {
    handler.orders.put(order.reference(), order)
  }

  Object sentValue(AttributeDescriptor<?> attribute) {
    return sent.reverse().find { it.name == attribute.name }?.value?.orElse(null)
  }
}
