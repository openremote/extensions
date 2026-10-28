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
package org.openremote.extension.ems.manager

import org.openremote.extension.ems.agent.EmsDistroEnergyAsset
import org.openremote.extension.ems.agent.EmsGOPACSAsset
import org.openremote.manager.asset.AssetProcessingService
import org.openremote.manager.asset.AssetStorageService
import org.openremote.model.PersistenceEvent
import org.openremote.model.asset.Asset
import org.openremote.model.attribute.AttributeEvent
import org.openremote.model.query.AssetQuery
import org.openremote.model.util.ValueUtil
import spock.lang.Specification

/**
 * Exercises how {@link EmsOptimisationService} copies the announcement inputs from an
 * {@link EmsGOPACSAsset} to the {@link EmsDistroEnergyAsset}s that share its parent. Storage hands
 * out the assets in {@code storedAssets}, answering {@code findAll} by parent and type, and every
 * attribute event the service sends is recorded in {@code sent}.
 */
class EmsOptimisationServiceGopacsInputsTest extends Specification {

  static final String PARENT_ID = "optimisationAsset1"
  static final String GOPACS_ID = "gopacsAsset1"
  static final String DISTRO_ID = "distroAsset1"
  static final String OTHER_DISTRO_ID = "distroAsset2"
  static final String EAN = "ean.265987182507322951"
  static final String ANNOUNCEMENT_ID = "35b6767a-7be2-49d4-a743-2045674b7138"
  static final long START = 1_800_000_000_000L
  static final long END = START + 1_800_000L
  static final long BID_VALIDITY_END = START - 3_600_000L
  static final String EFFECTIVITY = "BUY_LOW_SELL_HIGH"

  List<Asset<?>> storedAssets
  List<AttributeEvent> sent
  EmsOptimisationService service

  def setupSpec() {
    // Populates the asset model registry from this extension's own AssetModelProvider SPI
    // registration, which real asset construction needs.
    ValueUtil.initialise(null)
  }

  def setup() {
    storedAssets = []
    sent = []
    def assetStorageService = Stub(AssetStorageService) {
      find(_ as String) >> { String assetId -> storedAssets.find { it.id == assetId } }
      findAll(_ as AssetQuery) >> { AssetQuery query ->
        storedAssets.findAll {
          it.type in query.types && it.parentId in query.parents*.id
        }
      }
    }
    def assetProcessingService = Stub(AssetProcessingService) {
      sendAttributeEvent(_ as AttributeEvent, _ as String) >> { AttributeEvent event, String source ->
        sent << event
      }
    }
    service = new EmsOptimisationService()
    service.services = Services.builder()
            .withAssetStorageService(assetStorageService)
            .withAssetProcessingService(assetProcessingService)
            .build()
  }

  private static EmsGOPACSAsset gopacsAsset() {
    def asset = new EmsGOPACSAsset("gopacs").setId(GOPACS_ID).setParentId(PARENT_ID)
    asset.getAttributes().getOrCreate(EmsGOPACSAsset.CONTRACTED_EAN).setValue(EAN)
    asset.getAttributes().getOrCreate(EmsGOPACSAsset.REDISPATCH_ANNOUNCEMENT_ID)
            .setValue(ANNOUNCEMENT_ID)
    asset.getAttributes().getOrCreate(EmsGOPACSAsset.REDISPATCH_START_TIME).setValue(START)
    asset.getAttributes().getOrCreate(EmsGOPACSAsset.REDISPATCH_END_TIME).setValue(END)
    asset.getAttributes().getOrCreate(EmsGOPACSAsset.REDISPATCH_BID_VALIDITY_END)
            .setValue(BID_VALIDITY_END)
    asset.getAttributes().getOrCreate(EmsGOPACSAsset.REDISPATCH_EAN_EFFECTIVITY)
            .setValue(EFFECTIVITY)
    return asset
  }

  /** A Distro asset with every announcement input present and empty. */
  private static EmsDistroEnergyAsset distroAsset(String assetId = DISTRO_ID) {
    def asset = new EmsDistroEnergyAsset("distro").setId(assetId).setParentId(PARENT_ID)
    EmsOptimisationService.GOPACS_TO_DISTRO_INPUTS.values().each {
      asset.getAttributes().getOrCreate(it)
    }
    return asset
  }

  private static PersistenceEvent<Asset<?>> event(PersistenceEvent.Cause cause, Asset<?> asset) {
    return new PersistenceEvent<>(cause, asset, null, null, null)
  }

  private Map<String, Object> sentTo(String assetId) {
    return sent.findAll { it.id == assetId }.collectEntries { [(it.name): it.value.orElse(null)] }
  }

  static final Map<String, Object> ALL_INPUTS = [
    (EmsDistroEnergyAsset.GOPACS_EAN.name): EAN,
    (EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID.name): ANNOUNCEMENT_ID,
    (EmsDistroEnergyAsset.GOPACS_START_TIME.name): START,
    (EmsDistroEnergyAsset.GOPACS_END_TIME.name): END,
    (EmsDistroEnergyAsset.GOPACS_BID_VALIDITY_END.name): BID_VALIDITY_END,
    (EmsDistroEnergyAsset.GOPACS_EAN_EFFECTIVITY.name): EFFECTIVITY,
  ]

  def "a GOPACS announcement change is copied to every Distro asset next to it"() {
    given:
    def gopacs = gopacsAsset()
    storedAssets << gopacs << distroAsset() << distroAsset(OTHER_DISTRO_ID)

    when:
    service.processAttributeEventEmsGOPACSAsset(
            new AttributeEvent(GOPACS_ID, EmsGOPACSAsset.REDISPATCH_ANNOUNCEMENT_ID.name,
            ANNOUNCEMENT_ID))

    then:
    sentTo(DISTRO_ID) == ALL_INPUTS
    sentTo(OTHER_DISTRO_ID) == ALL_INPUTS
  }

  def "inputs that already match are not written again"() {
    given:
    def distro = distroAsset()
    distro.getAttributes().getOrCreate(EmsDistroEnergyAsset.GOPACS_EAN).setValue(EAN)
    distro.getAttributes().getOrCreate(EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID)
            .setValue(ANNOUNCEMENT_ID)
    storedAssets << gopacsAsset() << distro

    when:
    service.processAttributeEventEmsGOPACSAsset(
            new AttributeEvent(GOPACS_ID, EmsGOPACSAsset.REDISPATCH_START_TIME.name, START))

    then:
    sentTo(DISTRO_ID).keySet() == ALL_INPUTS.keySet() - [
      EmsDistroEnergyAsset.GOPACS_EAN.name,
      EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID.name
    ]
  }

  def "a closed announcement clears the Distro inputs"() {
    given:
    def gopacs = gopacsAsset()
    gopacs.getAttributes().getOrCreate(EmsGOPACSAsset.REDISPATCH_ANNOUNCEMENT_ID).setValue(null)
    def distro = distroAsset()
    distro.getAttributes().getOrCreate(EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID)
            .setValue(ANNOUNCEMENT_ID)
    storedAssets << gopacs << distro

    when:
    service.processAttributeEventEmsGOPACSAsset(
            new AttributeEvent(GOPACS_ID, EmsGOPACSAsset.REDISPATCH_ANNOUNCEMENT_ID.name, null))

    then:
    sentTo(DISTRO_ID).containsKey(EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID.name)
    sentTo(DISTRO_ID)[EmsDistroEnergyAsset.GOPACS_ANNOUNCEMENT_ID.name] == null
  }

  def "a GOPACS attribute outside the announcement inputs copies nothing"() {
    given:
    storedAssets << gopacsAsset() << distroAsset()

    when:
    service.processAttributeEventEmsGOPACSAsset(
            new AttributeEvent(GOPACS_ID, EmsGOPACSAsset.REDISPATCH_BID_PRICE.name, 50d))

    then:
    sent.isEmpty()
  }

  def "a Distro asset created next to a GOPACS asset gets its inputs"() {
    given:
    def distro = distroAsset()
    storedAssets << gopacsAsset() << distro

    when:
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, distro))

    then:
    sentTo(DISTRO_ID) == ALL_INPUTS
  }

  def "a GOPACS asset created next to a Distro asset fills its inputs"() {
    given:
    def gopacs = gopacsAsset()
    storedAssets << gopacs << distroAsset()

    when:
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, gopacs))

    then:
    sentTo(DISTRO_ID) == ALL_INPUTS
  }

  def "nothing is copied while the Distro asset has more than one GOPACS sibling"() {
    given:
    def distro = distroAsset()
    def otherGopacs = gopacsAsset().setId("gopacsAsset2")
    storedAssets << gopacsAsset() << otherGopacs << distro

    when:
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, distro))

    then:
    sent.isEmpty()
  }

  def "an input the Distro asset lacks is skipped and the others are still copied"() {
    given:
    def distro = distroAsset()
    distro.getAttributes().remove(EmsDistroEnergyAsset.GOPACS_EAN.name)
    storedAssets << gopacsAsset() << distro

    when:
    service.processAssetChange(event(PersistenceEvent.Cause.CREATE, distro))

    then:
    sentTo(DISTRO_ID) == ALL_INPUTS - [(EmsDistroEnergyAsset.GOPACS_EAN.name): EAN]
  }
}
