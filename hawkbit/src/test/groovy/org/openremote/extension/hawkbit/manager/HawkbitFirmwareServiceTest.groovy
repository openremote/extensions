/*
 * Copyright 2025, OpenRemote Inc.
 *
 * See the CONTRIBUTORS.txt file in the distribution for a
 * full listing of individual contributors.
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
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.openremote.extension.hawkbit.manager

import jakarta.ws.rs.core.Response
import org.openremote.extension.hawkbit.manager.hawkbit.HawkbitTargetsClient
import org.openremote.extension.hawkbit.model.FirmwareMetaItemType
import org.openremote.extension.hawkbit.model.hawkbit.MetadataUpdateRequest
import org.openremote.extension.hawkbit.model.hawkbit.Target
import org.openremote.model.asset.Asset
import org.openremote.model.asset.AssetEvent
import org.openremote.model.attribute.*
import org.openremote.test.ManagerContainerTrait
import spock.lang.Specification

import static org.openremote.model.value.ValueType.TEXT

class HawkbitFirmwareServiceTest extends Specification implements ManagerContainerTrait {

    private static final String CONTROLLER_ID = "test-asset-id"

    /**
     * Subclass that bypasses asset type descriptor lookup so asset sync logic can be tested
     * with any asset type.
     */
    static class TestableHawkbitFirmwareService extends HawkbitFirmwareService {
        protected Optional<String> getFirmwareTargetInfoAttributeName(Asset asset) {
            return Optional.of("firmwareTarget")
        }
    }

    def "getFirmwareTargetInfoAttributeName rejects multiple marked attributes"() {
        given: "an asset with multiple attributes marked as firmware target"
        def service = new HawkbitFirmwareService()
        def meta = new MetaMap()
        meta.put(new MetaItem<>(FirmwareMetaItemType.FIRMWARE_TARGET, true))

        def asset = Mock(Asset)
        asset.getType() >> "unknown:asset:type"
        asset.getAttributes() >> new AttributeMap([
                new Attribute<>("firmwareTargetInfo", TEXT).setMeta(meta),
                new Attribute<>("otherFirmwareTargetInfo", TEXT).setMeta(meta)
        ])

        expect: "ambiguous firmware target info attributes are ignored"
        service.getFirmwareTargetInfoAttributeName(asset) == Optional.empty()
    }

    def "handleAssetChange with CREATE cause creates target when it does not exist"() {
        given: "a service with mocked targets client"
        def service = new TestableHawkbitFirmwareService()
        service.targets = Mock(HawkbitTargetsClient)
        def asset = Mock(Asset)
        asset.getId() >> CONTROLLER_ID
        asset.getRealm() >> "master"
        asset.getAttributes() >> new AttributeMap()

        when: "handling an asset CREATE event"
        service.handleAssetChange(new AssetEvent(AssetEvent.Cause.CREATE, asset))

        then: "hawkBit creates the target and then queries it to update target info"
        1 * service.targets.create(_) >> Response.ok().build()
        1 * service.targets.get(CONTROLLER_ID) >> Response.ok(new Target(CONTROLLER_ID, null, null, "token", null, null, null, null, null, null, null, null, null)).build()
    }

    def "handleAssetChange with UPDATE cause skips create when target already exists"() {
        given: "a service with mocked targets client returning an existing target"
        def service = new TestableHawkbitFirmwareService()
        def existingTarget = new Target(CONTROLLER_ID, null, null, "token", null, null, null, null, null, null, null, null, null)
        service.targets = Mock(HawkbitTargetsClient)

        def asset = Mock(Asset)
        asset.getId() >> CONTROLLER_ID
        asset.getRealm() >> "master"
        asset.getAttributes() >> new AttributeMap()

        when: "handling an asset UPDATE event"
        service.handleAssetChange(new AssetEvent(AssetEvent.Cause.UPDATE, asset))

        then: "hawkBit is queried but create is not called"
        2 * service.targets.get(CONTROLLER_ID) >> Response.ok(existingTarget).build()
        0 * service.targets.create(_)
    }

    def "handleAssetChange with UPDATE cause creates target when it is missing"() {
        given: "a service with mocked targets client returning 404 then the created target"
        def service = new TestableHawkbitFirmwareService()
        service.targets = Mock(HawkbitTargetsClient)

        def asset = Mock(Asset)
        asset.getId() >> CONTROLLER_ID
        asset.getRealm() >> "master"
        asset.getAttributes() >> new AttributeMap()

        when: "handling an asset UPDATE event for a missing target"
        service.handleAssetChange(new AssetEvent(AssetEvent.Cause.UPDATE, asset))

        then: "hawkBit queries (404), creates, then queries again for info update"
        2 * service.targets.get(CONTROLLER_ID) >>> [
                Response.status(Response.Status.NOT_FOUND).build(),
                Response.ok(new Target(CONTROLLER_ID, null, null, "token", null, null, null, null, null, null, null, null, null)).build()
        ]
        1 * service.targets.create(_) >> Response.ok().build()
    }

    def "handleAssetChange with UPDATE cause logs warning when getFirmwareTarget throws exception"() {
        given: "a service with mocked targets client that throws"
        def service = new TestableHawkbitFirmwareService()
        service.targets = Mock(HawkbitTargetsClient)

        def asset = Mock(Asset)
        asset.getId() >> CONTROLLER_ID
        asset.getRealm() >> "master"

        when: "handling an asset UPDATE event when hawkBit query fails"
        service.handleAssetChange(new AssetEvent(AssetEvent.Cause.UPDATE, asset))

        then: "hawkBit is queried but throws, no create is attempted"
        1 * service.targets.get(CONTROLLER_ID) >> { throw new RuntimeException("connection failed") }
        0 * service.targets.create(_)
        0 * service.targets.delete(_)
    }

    def "handleAssetChange with DELETE cause deletes target"() {
        given: "a service with mocked targets client"
        def service = new TestableHawkbitFirmwareService()
        service.targets = Mock(HawkbitTargetsClient)

        def asset = Mock(Asset)
        asset.getId() >> CONTROLLER_ID
        asset.getRealm() >> "master"

        when: "handling an asset DELETE event"
        service.handleAssetChange(new AssetEvent(AssetEvent.Cause.DELETE, asset))

        then: "the delete endpoint is invoked"
        1 * service.targets.delete(CONTROLLER_ID) >> Response.ok().build()
    }

    def "handleAttributeChange returns early when attribute has no firmware metadata"() {
        given: "a service with mocked targets client"
        def service = new TestableHawkbitFirmwareService()
        service.targets = Mock(HawkbitTargetsClient)

        when: "handling an attribute event without firmware metadata"
        service.handleAttributeChange(new AttributeEvent(CONTROLLER_ID, "temp", 25))

        then: "no hawkBit calls are made"
        0 * service.targets.updateMetadata(_, _, _)
        0 * service.targets.deleteMetadata(_, _)
    }

    def "handleAttributeChange deletes metadata when attribute event is marked deleted"() {
        given: "a service with mocked targets client"
        def service = new TestableHawkbitFirmwareService()
        service.targets = Mock(HawkbitTargetsClient) {
            deleteMetadata(_ as String, _ as String) >> Response.ok().build()
        }
        def meta = new MetaMap()
        meta.put(new MetaItem<>(FirmwareMetaItemType.FIRMWARE_METADATA, true))

        def event = new AttributeEvent(CONTROLLER_ID, "temp", 25)
        event.setMeta(meta)
        event.setDeleted(true)

        when: "handling a deleted attribute event with firmware metadata"
        service.handleAttributeChange(event)

        then: "deleteMetadata is called and updateMetadata is not"
        1 * service.targets.deleteMetadata(CONTROLLER_ID, "temp")
        0 * service.targets.updateMetadata(_, _, _)
    }

    def "handleAttributeChange updates metadata when attribute has firmware metadata"() {
        given: "a service with mocked targets client"
        def service = new TestableHawkbitFirmwareService()
        service.targets = Mock(HawkbitTargetsClient) {
            updateMetadata(_ as String, _ as String, _ as MetadataUpdateRequest) >> Response.ok().build()
        }
        def meta = new MetaMap()
        meta.put(new MetaItem<>(FirmwareMetaItemType.FIRMWARE_METADATA, true))

        def event = new AttributeEvent(CONTROLLER_ID, "temp", 25)
        event.setMeta(meta)

        when: "handling an attribute event with a metadata value"
        service.handleAttributeChange(event)

        then: "updateMetadata is called with the string coerced value"
        1 * service.targets.updateMetadata(CONTROLLER_ID, "temp", _)
    }

    def "handleAttributeChange deletes metadata when attribute value is empty"() {
        given: "a service with mocked targets client"
        def service = new TestableHawkbitFirmwareService()
        service.targets = Mock(HawkbitTargetsClient) {
            deleteMetadata(_ as String, _ as String) >> Response.ok().build()
        }
        def meta = new MetaMap()
        meta.put(new MetaItem<>(FirmwareMetaItemType.FIRMWARE_METADATA, true))

        def event = new AttributeEvent(CONTROLLER_ID, "temp", "")
        event.setMeta(meta)

        when: "handling an attribute event with an empty value"
        service.handleAttributeChange(event)

        then: "deleteMetadata is called"
        1 * service.targets.deleteMetadata(CONTROLLER_ID, "temp")
    }
}
