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
import jakarta.ws.rs.WebApplicationException
import org.openremote.container.timer.TimerService
import org.openremote.extension.ems.agent.EmsDistroEnergyAsset
import org.openremote.extension.ems.agent.EmsDistroEnergyAsset.GopacsOrderTypeValueType
import org.openremote.extension.ems.agent.EmsElectricityBatteryAsset
import org.openremote.extension.ems.agent.EmsElectricityBatteryAsset.EmsElectricityBatteryConnectionStatusValueType
import org.openremote.extension.ems.manager.distroenergy.dto.GopacsOrder
import org.openremote.extension.ems.manager.distroenergy.dto.GopacsOrderRequest
import org.openremote.extension.ems.manager.distroenergy.dto.GopacsSettlement
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
import java.util.concurrent.TimeUnit

import static org.openremote.extension.ems.agent.EmsDistroEnergyAsset.GopacsOrderTypeValueType.BUY
import static org.openremote.extension.ems.agent.EmsDistroEnergyAsset.GopacsOrderTypeValueType.SELL

/**
 * Covers the order sizing rules and the attribute-driven order lifecycle of {@link
 * DistroGopacsHandler}. The Distro API is a Spock mock of {@link DistroGopacsResource} handed to the
 * test-support constructor, and the executor runs submitted work inline, so every reaction happens
 * on the test thread.
 */
class DistroGopacsHandlerTest extends Specification {

  static final String ASSET_ID = "distroAsset1"
  static final String PARENT_ID = "optimisationAsset1"
  static final String PORTFOLIO = "slimstrandnet"
  static final String EAN = "871685920001768800"
  static final String ANNOUNCEMENT = "4f1c2b3a-0000-4000-8000-000000000001"
  static final long NOW = Instant.parse("2026-10-22T10:00:00Z").toEpochMilli()
  static final long START = Instant.parse("2026-10-22T14:00:00Z").toEpochMilli()
  static final long END = Instant.parse("2026-10-22T16:00:00Z").toEpochMilli()
  static final long DAY = 24 * 60 * 60 * 1000L
  static final long VALIDITY_END = Instant.parse("2026-10-22T12:07:00Z").toEpochMilli()

  DistroGopacsResource resource
  EmsDistroEnergyAsset asset
  List<EmsElectricityBatteryAsset> batteries
  List<AttributeEvent> sent
  DistroGopacsHandler handler
  long now = NOW
  List<Map> scheduled
  boolean executorShutdown

  def setupSpec() {
    // Asset construction needs the attribute descriptors of this extension's asset types.
    ValueUtil.initialise(null)
  }

  def setup() {
    resource = Mock(DistroGopacsResource)
    asset = new EmsDistroEnergyAsset("distro").setId(ASSET_ID).setParentId(PARENT_ID)
    batteries = [battery()]
    sent = []
    scheduled = []
    executorShutdown = false

    def assetProcessingService = Stub(AssetProcessingService) {
      sendAttributeEvent(_, _) >> { AttributeEvent event, String source -> sent << event }
    }
    def assetStorageService = Stub(AssetStorageService) {
      find(ASSET_ID) >> { asset }
      findAll(_) >> { batteries }
    }
    def timerService = Stub(TimerService) {
      getCurrentTimeMillis() >> { now }
      getNow() >> { Instant.ofEpochMilli(now) }
    }
    def executor = Stub(ScheduledExecutorService) {
      execute(_) >> { Runnable task -> task.run() }
      scheduleAtFixedRate(*_) >> Stub(ScheduledFuture)
      isShutdown() >> { executorShutdown }
      schedule(_ as Runnable, _, _) >> { Runnable task, long delay, TimeUnit unit ->
        scheduled << [task: task, delayMillis: unit.toMillis(delay)]
        return Stub(ScheduledFuture)
      }
    }
    def container = Stub(Container) {
      getService(TimerService) >> timerService
      getService(AssetStorageService) >> assetStorageService
      getService(AssetProcessingService) >> assetProcessingService
      getScheduledExecutor() >> executor
    }
    handler = new DistroGopacsHandler(ASSET_ID, PORTFOLIO, container, resource)
  }

  // --- sizing rules ---

  def "category #category with buy #buyKw kW and sell #sellKw kW favours #expected"() {
    expect:
    DistroGopacsHandler.orderTypeFor(category, buyKw, sellKw) == expected

    where:
    category | buyKw | sellKw || expected
    "BUY_MAXIMUM_SELL_NONE" | 0 | 500 || BUY
    "BUY_VERY_HIGH_SELL_VERY_LOW" | 0 | 500 || BUY
    "BUY_HIGH_SELL_LOW" | 0 | 500 || BUY
    "BUY_LOW_SELL_HIGH" | 500 | 0 || SELL
    "BUY_VERY_LOW_SELL_VERY_HIGH" | 500 | 0 || SELL
    "BUY_NONE_SELL_MAXIMUM" | 500 | 0 || SELL
    "BUY_MODERATE_SELL_MODERATE" | 300 | 200 || BUY
    "BUY_MODERATE_SELL_MODERATE" | 200 | 300 || SELL
    "BUY_MODERATE_SELL_MODERATE" | 200 | 200 || BUY
    "BUY_NONE_SELL_NONE" | 500 | 500 || null
    "THREE_PHASE_NETWORK_REDUCE" | 500 | 500 || null
    "BUY_SOME_SELL_NONE" | 500 | 500 || null
    null | 500 | 500 || null
  }

  def "quantity is the battery power the state of charge can hold for the window: #label"() {
    expect:
    DistroGopacsHandler.quantityKw(fleet, orderType, hours) == expectedKw

    where:
    label | fleet | orderType | hours || expectedKw
    "BUY limited by energy headroom" | [battery()] | BUY | 2 || 200
    "BUY limited by power" | [battery()] | BUY | 0.5 || 500
    "SELL uses the negative discharge" | [battery()] | SELL | 0.5 || 400
    "SELL limited by energy headroom" | [battery()] | SELL | 2 || 200
    "charge efficiency raises grid energy" | [battery(chargeEfficiency: 80)] | BUY | 2 || 200
    "discharge efficiency lowers it" | [battery(dischargeEfficiency: 50)] | SELL | 2 || 100
    "batteries add up" | [battery(), battery()] | BUY | 2 || 400
    "one battery's energy cannot cover another's power" | [battery(capacity: 250d), battery(capacity: 2500d, chargeMax: 100d)] | BUY | 1 || 200
    "rounded down to 100 kW steps" | [battery(level: 45d)] | SELL | 2 || 100
    "charging not allowed" | [battery(allowCharging: false)] | BUY | 2 || 0
    "disconnected" | [battery(connection: EmsElectricityBatteryConnectionStatusValueType.disconnected)] | BUY | 2 || 0
    "already full" | [battery(level: 90d)] | BUY | 2 || 0
    "no state of charge" | [battery(level: null)] | BUY | 2 || 0
    "empty window" | [battery()] | BUY | 0 || 0
  }

  def "expiration #label"() {
    expect:
    DistroGopacsHandler.expirationSeconds(seconds(START), validityEnd, seconds(NOW)) == expected

    where:
    label | validityEnd || expected
    "is the bid validity end on a quarter" | seconds("2026-10-22T12:00:00Z") || seconds("2026-10-22T12:00:00Z")
    "is rounded down to a quarter-hour" | seconds("2026-10-22T12:07:00Z") || seconds("2026-10-22T12:00:00Z")
    "is left to Distro when unknown" | null || null
    "may be 15 minutes before start" | seconds("2026-10-22T13:50:00Z") || seconds("2026-10-22T13:45:00Z")
    "is left to Distro when after its default" | seconds("2026-10-22T14:10:00Z") || null
    "is left to Distro when already passed" | seconds("2026-10-22T09:00:00Z") || null
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
    1 * resource.createOrder(PORTFOLIO, _) >> { String portfolio, GopacsOrderRequest body ->
      request = body
      return stored(body.reference(), "PENDING", ANNOUNCEMENT)
    }
    request.reference() ==~ /[a-z0-9-]{36}/
    request.orderType() == "SELL"
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
    1 * resource.createOrder(PORTFOLIO, {
      it.quantity() == 0.2d && it.price() == 45.68d
    }) >> { String portfolio, GopacsOrderRequest body ->
      stored(body.reference(), "SUBMITTED", ANNOUNCEMENT)
    }
  }

  def "confirming strips the GOPACS ean. prefix from the EAN: #configured"() {
    given:
    announcement("BUY_LOW_SELL_HIGH")
    order(SELL, 200, 45)
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_EAN).setValue(configured)

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER.name, false, true)

    then:
    1 * resource.createOrder(PORTFOLIO, {
      it.ean() == EAN
    }) >> { String portfolio, GopacsOrderRequest body ->
      stored(body.reference(), "SUBMITTED", ANNOUNCEMENT)
    }

    where:
    configured << ["ean." + EAN, "EAN." + EAN, " " + EAN + " "]
  }

  def "confirming refuses what Distro would reject: #label"() {
    given:
    announcement("BUY_LOW_SELL_HIGH")
    order(SELL, quantityKw, price)
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_START_TIME).setValue(start)
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_BID_VALIDITY_END).setValue(validityEnd)

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER.name, false, true)

    then:
    0 * resource.createOrder(*_)
    (sentValue(EmsDistroEnergyAsset.GOPACS_ORDER_HISTORY) as Map).detail == detail

    where:
    label | quantityKw | price | start | validityEnd || detail
    "a quantity rounded to 0" | 99 | 45 | START | VALIDITY_END || "quantity 99.0 kW is below the 100 kW minimum"
    "less than 15 minutes left" | 200 | 45 | Instant.parse("2026-10-22T10:15:00Z").toEpochMilli() | VALIDITY_END || "window starts in less than 15 minutes"
    "bid validity end passed" | 200 | 45 | START | NOW || "bid validity end has passed"
  }

  def "a failed create is recorded and nothing is tracked"() {
    given:
    announcement("BUY_LOW_SELL_HIGH")
    order(SELL, 200, 45)

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER.name, false, true)

    then:
    1 * resource.createOrder(PORTFOLIO, _) >> { throw new WebApplicationException(403) }
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
    1 * resource.createOrder(PORTFOLIO, _) >> { throw new ProcessingException("Read timed out") }
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
    1 * resource.replaceOrder(PORTFOLIO, "open-current", {
      it.price() == 60d && it.reference() == "open-current"
    }) >>
    stored("open-current", "SUBMITTED", ANNOUNCEMENT, 60)
    0 * resource.replaceOrder(*_)
    handler.orders["open-current"].price() == 60d
  }

  def "a new announcement cancels the open orders placed for the previous one"() {
    given:
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID).setValue("next")
    track(stored("open-previous", "PENDING", ANNOUNCEMENT))
    track(stored("locked-previous", "LOCKED", ANNOUNCEMENT))
    track(stored("open-next", "PENDING", "next"))

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID.name, ANNOUNCEMENT, "next")

    then:
    1 * resource.cancelOrder(PORTFOLIO, "open-previous")
    0 * resource.cancelOrder(*_)
    handler.orders.keySet() == ["locked-previous", "open-next"] as Set
  }

  def "an announcement change cancels against the asset's current announcement, not the event's"() {
    given: "the asset already moved on to the next announcement when the older change runs"
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID).setValue("latest")
    track(stored("for-middle", "SUBMITTED", "middle"))
    track(stored("for-latest", "SUBMITTED", "latest"))

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID.name, ANNOUNCEMENT, "middle")

    then:
    1 * resource.cancelOrder(PORTFOLIO, "for-middle")
    0 * resource.cancelOrder(*_)
    handler.orders.keySet() == ["for-latest"] as Set
  }

  def "a closed announcement cancels every open order"() {
    given: "no current announcement"
    track(stored("a", "PENDING", ANNOUNCEMENT))
    track(stored("b", "SUBMITTED", "older"))

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID.name, ANNOUNCEMENT, null)

    then:
    1 * resource.cancelOrder(PORTFOLIO, "a")
    1 * resource.cancelOrder(PORTFOLIO, "b")
    handler.orders.isEmpty()
    (sentValue(EmsDistroEnergyAsset.GOPACS_ORDERS) as Map).isEmpty()
  }

  def "a poll does not cancel an order it could not refresh"() {
    given: "no current announcement"
    track(stored("unreachable", "SUBMITTED", ANNOUNCEMENT))

    when:
    handler.pollOrders()

    then:
    1 * resource.getOrder(PORTFOLIO, "unreachable") >> {
      throw new ProcessingException("Connect timed out")
    }
    0 * resource._
    sent.findAll { it.name == EmsDistroEnergyAsset.GOPACS_ORDER_HISTORY.name }.isEmpty()
  }

  def "a poll cancels the open orders of an announcement that closed"() {
    given: "no current announcement"
    track(stored("open", "SUBMITTED", ANNOUNCEMENT))
    track(stored("locked", "SUBMITTED", ANNOUNCEMENT))

    when:
    handler.pollOrders()

    then:
    1 * resource.getOrder(PORTFOLIO, "open") >> stored("open", "SUBMITTED", ANNOUNCEMENT)
    1 * resource.getOrder(PORTFOLIO, "locked") >> stored("locked", "LOCKED", ANNOUNCEMENT)
    1 * resource.cancelOrder(PORTFOLIO, "open")
    1 * resource.listSettlements(PORTFOLIO, "open") >> []
    0 * resource._
    handler.orders.keySet() == ["locked"] as Set
    (sentValue(EmsDistroEnergyAsset.GOPACS_ORDERS) as Map).keySet() == ["locked"] as Set
  }

  def "a poll retries a cancel that failed when the announcement changed"() {
    given:
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID).setValue("next")
    track(stored("previous", "SUBMITTED", ANNOUNCEMENT))
    track(stored("current", "SUBMITTED", "next"))

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID.name, ANNOUNCEMENT, "next")

    then:
    1 * resource.cancelOrder(PORTFOLIO, "previous") >> { throw new WebApplicationException(500) }
    handler.orders.keySet() == ["previous", "current"] as Set

    when:
    handler.pollOrders()

    then:
    1 * resource.getOrder(PORTFOLIO, "previous") >> stored("previous", "SUBMITTED", ANNOUNCEMENT)
    1 * resource.getOrder(PORTFOLIO, "current") >> stored("current", "SUBMITTED", "next")
    1 * resource.cancelOrder(PORTFOLIO, "previous")
    1 * resource.listSettlements(PORTFOLIO, "previous") >> []
    0 * resource._
    handler.orders.keySet() == ["current"] as Set
  }

  def "the cancel flag cancels every open order and is reset"() {
    given:
    track(stored("a", "PENDING", ANNOUNCEMENT))
    track(stored("b", "SUBMITTED", "other-announcement"))
    track(stored("c", "LOCKED", ANNOUNCEMENT))

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_CANCEL_ORDERS.name, false, true)

    then:
    1 * resource.cancelOrder(PORTFOLIO, "a")
    1 * resource.cancelOrder(PORTFOLIO, "b")
    0 * resource.cancelOrder(*_)
    handler.orders.keySet() == ["c"] as Set
  }

  def "polling keeps live orders, records settled trades and drops final orders"() {
    given:
    track(stored("live", "SUBMITTED", ANNOUNCEMENT))
    track(stored("settled", "SUBMITTED", ANNOUNCEMENT))
    track(stored("expired", "SUBMITTED", ANNOUNCEMENT))
    track(stored("gone", "PENDING", ANNOUNCEMENT))

    when:
    handler.pollOrders()

    then:
    1 * resource.getOrder(PORTFOLIO, "live") >> stored("live", "LOCKED", ANNOUNCEMENT)
    1 * resource.getOrder(PORTFOLIO, "settled") >> stored("settled", "SETTLED", ANNOUNCEMENT)
    1 * resource.getOrder(PORTFOLIO, "expired") >> stored("expired", "EXPIRED", ANNOUNCEMENT)
    1 * resource.getOrder(PORTFOLIO, "gone") >> { throw new NotFoundException() }
    1 * resource.listSettlements(PORTFOLIO, "settled") >> [trade("trade-1", "settled"), trade("trade-2", "settled")]
    1 * resource.listSettlements(PORTFOLIO, "expired") >> []
    0 * resource.listSettlements(*_)
    handler.orders.keySet() == ["live"] as Set
    handler.orders["live"].status() == "LOCKED"
    def recorded = sent.findAll {
      it.name == EmsDistroEnergyAsset.GOPACS_SETTLEMENT_HISTORY.name
    }*.value*.get()
    recorded.size() == 1
    recorded[0].subMap(["reference", "recordedAt"]) == [reference: "settled", recordedAt: NOW]
    recorded[0].trades*.tradeId == ["trade-1", "trade-2"]
    recorded[0].trades[0].subMap(["ean", "capacityUnit", "volumeUnit", "priceUnit", "modifiedAt"]) ==
    [ean: EAN, capacityUnit: "MW", volumeUnit: "MWH", priceUnit: "EUR/MWH", modifiedAt: seconds(NOW)]
    sentValue(EmsDistroEnergyAsset.GOPACS_LAST_SYNC) == NOW
  }

  def "trades that #label keep the settled order tracked: #tracked"() {
    given:
    now = clock
    track(stored("settled", "SUBMITTED", ANNOUNCEMENT))

    when:
    handler.pollOrders()

    then:
    1 * resource.getOrder(PORTFOLIO, "settled") >> stored("settled", "SETTLED", ANNOUNCEMENT)
    1 * resource.listSettlements(PORTFOLIO, "settled") >> {
      if (answer instanceof Exception) {
        throw answer
      }
      answer
    }
    handler.orders.containsKey("settled") == tracked
    sent.findAll {
      it.name == EmsDistroEnergyAsset.GOPACS_ORDER_HISTORY.name
    }*.value*.get()*.action == actions
    sent.findAll { it.name == EmsDistroEnergyAsset.GOPACS_SETTLEMENT_HISTORY.name }.isEmpty()

    where:
    label | answer | clock || tracked | actions
    "time out" | new ProcessingException("Read timed out") | NOW || true | ["STATUS"]
    "fail with HTTP 500" | new WebApplicationException(500) | NOW || true | ["STATUS"]
    "fail with HTTP 401" | new WebApplicationException(401) | NOW || true | ["STATUS"]
    "are not there yet" | [] | NOW || true | ["STATUS"]
    "are forbidden" | new WebApplicationException(403) | NOW || false | ["STATUS", "SETTLEMENTS_FAILED"]
    "still time out a day after the window" | new ProcessingException("Read timed out") | END + DAY || false | ["STATUS", "SETTLEMENTS_FAILED"]
    "are still missing a day after the window" | [] | END + DAY || false | ["STATUS", "SETTLEMENTS_FAILED"]
  }

  def "a partly matched order that ends #status records its trades"() {
    given:
    track(stored("partial", "SUBMITTED", ANNOUNCEMENT))

    when:
    handler.pollOrders()

    then:
    1 * resource.getOrder(PORTFOLIO, "partial") >> stored("partial", status, ANNOUNCEMENT)
    1 * resource.listSettlements(PORTFOLIO, "partial") >> [trade("trade-1", "partial")]
    handler.orders.isEmpty()
    sent.findAll {
      it.name == EmsDistroEnergyAsset.GOPACS_SETTLEMENT_HISTORY.name
    }*.value*.get()*.reference == ["partial"]

    where:
    status << ["EXPIRED", "CANCELED", "REJECTED"]
  }

  def "a cancelled order records the trades it matched before the cancel"() {
    given:
    track(stored("partial", "SUBMITTED", ANNOUNCEMENT))

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_CANCEL_ORDERS.name, false, true)

    then:
    1 * resource.cancelOrder(PORTFOLIO, "partial")
    1 * resource.listSettlements(PORTFOLIO, "partial") >> [trade("trade-1", "partial")]
    handler.orders.isEmpty()
    sent.findAll {
      it.name == EmsDistroEnergyAsset.GOPACS_ORDER_HISTORY.name
    }*.value*.get()*.subMap(["action", "status"]) == [[action: "CANCELED", status: "CANCELED"]]
    sent.findAll {
      it.name == EmsDistroEnergyAsset.GOPACS_SETTLEMENT_HISTORY.name
    }*.value*.get()*.reference == ["partial"]
  }

  def "a cancelled order whose trades fail to load stays tracked as cancelled"() {
    given:
    track(stored("partial", "SUBMITTED", ANNOUNCEMENT))

    when:
    handler.processAttributeChange(EmsDistroEnergyAsset.GOPACS_CANCEL_ORDERS.name, false, true)

    then:
    1 * resource.cancelOrder(PORTFOLIO, "partial")
    1 * resource.listSettlements(PORTFOLIO, "partial") >> {
      throw new ProcessingException("Read timed out")
    }
    handler.orders["partial"].status() == "CANCELED"
  }

  def "the next poll retries the trades of a settled order without recording its status again"() {
    given:
    track(stored("settled", "SUBMITTED", ANNOUNCEMENT))

    when:
    handler.pollOrders()

    then:
    1 * resource.getOrder(PORTFOLIO, "settled") >> stored("settled", "SETTLED", ANNOUNCEMENT)
    1 * resource.listSettlements(PORTFOLIO, "settled") >> {
      throw new ProcessingException("Read timed out")
    }
    handler.orders["settled"].status() == "SETTLED"

    when:
    handler.pollOrders()

    then:
    1 * resource.getOrder(PORTFOLIO, "settled") >> stored("settled", "SETTLED", ANNOUNCEMENT)
    1 * resource.listSettlements(PORTFOLIO, "settled") >> [trade("trade-1", "settled")]
    handler.orders.isEmpty()
    sent.findAll {
      it.name == EmsDistroEnergyAsset.GOPACS_ORDER_HISTORY.name
    }*.value*.get()*.action == ["STATUS"]
    sent.findAll {
      it.name == EmsDistroEnergyAsset.GOPACS_SETTLEMENT_HISTORY.name
    }*.value*.get()*.reference == ["settled"]
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
    track(stored("locked", "LOCKED", ANNOUNCEMENT))
    save(handler.orders.values() + [stored("next-handler", "SUBMITTED", ANNOUNCEMENT)])

    when:
    handler.undeploy(true)

    then:
    1 * resource.cancelOrder(PORTFOLIO, "pending")
    1 * resource.cancelOrder(PORTFOLIO, "submitted")
    1 * resource.listSettlements(PORTFOLIO, "pending") >> []
    1 * resource.listSettlements(PORTFOLIO, "submitted") >> []
    0 * resource._
    handler.orders.keySet() == ["locked"] as Set
    (sentValue(EmsDistroEnergyAsset.GOPACS_ORDERS) as Map).keySet() == ["locked", "next-handler"] as Set
    scheduled.isEmpty()
  }

  def "undeploying for good retries a cancel that failed with #label, for that order only"() {
    given:
    track(stored("flaky", "PENDING", ANNOUNCEMENT))
    track(stored("fine", "SUBMITTED", ANNOUNCEMENT))
    save(handler.orders.values())

    when:
    handler.undeploy(true)

    then:
    1 * resource.cancelOrder(PORTFOLIO, "flaky") >> { throw failure }
    1 * resource.cancelOrder(PORTFOLIO, "fine")
    1 * resource.listSettlements(PORTFOLIO, "fine") >> []
    0 * resource._
    scheduled*.delayMillis == [60_000L]
    (sentValue(EmsDistroEnergyAsset.GOPACS_ORDERS) as Map).keySet() == ["flaky"] as Set

    when:
    save(handler.orders.values())
    scheduled[0].task.run()

    then:
    1 * resource.cancelOrder(PORTFOLIO, "flaky")
    1 * resource.listSettlements(PORTFOLIO, "flaky") >> []
    0 * resource._
    handler.orders.isEmpty()
    scheduled.size() == 1
    (sentValue(EmsDistroEnergyAsset.GOPACS_ORDERS) as Map).isEmpty()

    where:
    label | failure
    "HTTP 502" | new WebApplicationException(502)
    "HTTP 401" | new WebApplicationException(401)
    "a timeout" | new ProcessingException("Read timed out")
  }

  def "undeploying for good with nothing open leaves the saved orders alone"() {
    given:
    track(stored("locked", "LOCKED", ANNOUNCEMENT))
    save([stored("settled-by-last-poll", "SUBMITTED", ANNOUNCEMENT)])

    when:
    handler.undeploy(true)

    then:
    0 * resource._
    sent.findAll { it.name == EmsDistroEnergyAsset.GOPACS_ORDERS.name }.isEmpty()
  }

  def "undeploying for good saves a cancelled order whose trades failed to load as cancelled"() {
    given:
    track(stored("partial", "SUBMITTED", ANNOUNCEMENT))
    save(handler.orders.values())

    when:
    handler.undeploy(true)

    then:
    1 * resource.cancelOrder(PORTFOLIO, "partial")
    1 * resource.listSettlements(PORTFOLIO, "partial") >> {
      throw new ProcessingException("Read timed out")
    }
    (sentValue(EmsDistroEnergyAsset.GOPACS_ORDERS) as Map).partial.status == "CANCELED"
    scheduled.isEmpty()
  }

  def "undeploying for good does not schedule a retry on an executor that shut down"() {
    given:
    executorShutdown = true
    track(stored("flaky", "SUBMITTED", ANNOUNCEMENT))

    when:
    handler.undeploy(true)

    then:
    1 * resource.cancelOrder(PORTFOLIO, "flaky") >> { throw new WebApplicationException(502) }
    scheduled.isEmpty()
  }

  def "undeploying for good gives up cancelling after five attempts over 15 minutes"() {
    given:
    track(stored("unreachable", "SUBMITTED", ANNOUNCEMENT))

    when:
    handler.undeploy(true)
    runScheduled()

    then:
    5 * resource.cancelOrder(PORTFOLIO, "unreachable") >> {
      throw new ProcessingException("Connect timed out")
    }
    0 * resource._
    scheduled*.delayMillis == [60_000L, 120_000L, 240_000L, 480_000L]
    handler.orders.keySet() == ["unreachable"] as Set
    sent.findAll {
      it.name == EmsDistroEnergyAsset.GOPACS_ORDER_HISTORY.name
    }*.value*.get()*.action == ["CANCEL_FAILED"] * 5
  }

  def "undeploying for good does not retry a cancel Distro refused with HTTP #status"() {
    given:
    track(stored("refused", "SUBMITTED", ANNOUNCEMENT))

    when:
    handler.undeploy(true)

    then:
    1 * resource.cancelOrder(PORTFOLIO, "refused") >> { throw new WebApplicationException(status) }
    scheduled.isEmpty()

    where:
    status << [404, 423]
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

  def "deploy resets a confirm flag left true by an earlier undeploy"() {
    given:
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER).setValue(true)
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_CANCEL_ORDERS).setValue(false)

    when:
    handler.deploy()

    then:
    sent*.name == [EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER.name]
    sentValue(EmsDistroEnergyAsset.GOPACS_CONFIRM_ORDER) == false
  }

  // --- helpers ---

  static long seconds(long millis) {
    return millis.intdiv(1000)
  }

  static long seconds(String instant) {
    return Instant.parse(instant).epochSecond
  }

  static EmsElectricityBatteryAsset battery(Map overrides = [:]) {
    Map values = [allowCharging : true, allowDischarging: true, capacity: 1000d, level: 50d,
      max : 90, min: 10, chargeMax: 500d, dischargeMax: -400d,
      chargeEfficiency : 100, dischargeEfficiency: 100,
      connection : EmsElectricityBatteryConnectionStatusValueType.connected] + overrides
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
    attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_BID_VALIDITY_END).setValue(VALIDITY_END)
    attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_EAN_EFFECTIVITY).setValue(category)
  }

  void order(GopacsOrderTypeValueType orderType, double quantityKw, double price) {
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_ORDER_TYPE).setValue(orderType)
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_ORDER_QUANTITY).setValue(quantityKw)
    asset.setGopacsOrderPrice(price)
  }

  static GopacsOrder stored(String reference, String status, String announcementId, double price = 45) {
    return new GopacsOrder(reference, null, status, "SELL", 0.2, price, seconds(START), seconds(END),
            EAN, [announcementId: announcementId], null, null)
  }

  static GopacsSettlement trade(String tradeId, String reference) {
    return new GopacsSettlement(tradeId, reference, EAN, "BUY", seconds(START), seconds(END),
            0.2, "MW", 0.4, "MWH", 45, "EUR/MWH", 18, seconds(NOW), seconds(NOW))
  }

  /** Runs the scheduled tasks in order, including those they schedule. */
  void runScheduled() {
    for (int i = 0; i <scheduled.size(); i++) {
      scheduled[i].task.run()
    }
  }

  /** Saves the orders on the asset, as {@code deploy()} finds them. */
  void save(Collection<GopacsOrder> toSave) {
    asset.attributes.getOrCreate(EmsDistroEnergyAsset.GOPACS_ORDERS)
            .setValue(ValueUtil.JSON.convertValue(toSave.collectEntries { [(it.reference()): it] },
            org.openremote.model.value.ValueType.ObjectMap))
  }

  void track(GopacsOrder order) {
    handler.orders.put(order.reference(), order)
  }

  Object sentValue(AttributeDescriptor<?> attribute) {
    return sent.reverse().find { it.name == attribute.name }?.value?.orElse(null)
  }
}
