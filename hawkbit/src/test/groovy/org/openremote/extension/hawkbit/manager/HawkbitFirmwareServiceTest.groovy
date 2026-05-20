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
import org.openremote.extension.hawkbit.model.hawkbit.TargetCreateRequest
import org.openremote.extension.hawkbit.model.hawkbit.TargetUpdateRequest
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
        Optional<String> getTargetInfoAttributeName(Asset asset) {
            return Optional.of("firmwareTarget")
        }
    }

    def "getTargetInfoAttributeName rejects multiple marked attributes"() {
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
        service.getTargetInfoAttributeName(asset) == Optional.empty()
    }

    def "handleAssetChange with CREATE cause creates target when it does not exist"() {
        given: "a service with mocked targets client"
        def service = new TestableHawkbitFirmwareService()
        service.targets = Mock(HawkbitTargetsClient)
        def createdTarget = new Target(CONTROLLER_ID, null, null, "token", null, null, null, null, null, null, null, null, null)
        def asset = Mock(Asset)
        asset.getId() >> CONTROLLER_ID
        asset.getRealm() >> "master"
        asset.getAttributes() >> new AttributeMap()

        when: "handling an asset CREATE event"
        service.handleAssetChange(new AssetEvent(AssetEvent.Cause.CREATE, asset))

        then: "hawkBit checks (404), creates, then uses the created target to update target info"
        1 * service.targets.get(CONTROLLER_ID) >> Response.status(Response.Status.NOT_FOUND).build()
        1 * service.targets.create({ TargetCreateRequest[] targets ->
            targets.length == 1 && targets[0].securityToken() == null
        }) >> Response.ok(new Target[]{createdTarget}).build()
    }

    def "handleAssetChange with CREATE cause skips create when target already exists"() {
        given: "a service with mocked targets client returning an existing target"
        def service = new TestableHawkbitFirmwareService()
        def existingTarget = new Target(CONTROLLER_ID, null, null, "token", null, null, null, null, null, null, null, null, null)
        service.targets = Mock(HawkbitTargetsClient)

        def asset = Mock(Asset)
        asset.getId() >> CONTROLLER_ID
        asset.getRealm() >> "master"
        asset.getAttributes() >> new AttributeMap()

        when: "handling an asset CREATE event for an already-existing target"
        service.handleAssetChange(new AssetEvent(AssetEvent.Cause.CREATE, asset))

        then: "hawkBit is queried but create is not called"
        1 * service.targets.get(CONTROLLER_ID) >> Response.ok(existingTarget).build()
        0 * service.targets.create(_)
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
        1 * service.targets.get(CONTROLLER_ID) >> Response.ok(existingTarget).build()
        0 * service.targets.create(_)
    }

    def "handleAssetChange with UPDATE cause creates target when it is missing"() {
        given: "a service with mocked targets client returning 404 then creating the target"
        def service = new TestableHawkbitFirmwareService()
        service.targets = Mock(HawkbitTargetsClient)
        def createdTarget = new Target(CONTROLLER_ID, null, null, "token", null, null, null, null, null, null, null, null, null)

        def asset = Mock(Asset)
        asset.getId() >> CONTROLLER_ID
        asset.getRealm() >> "master"
        asset.getAttributes() >> new AttributeMap()

        when: "handling an asset UPDATE event for a missing target"
        service.handleAssetChange(new AssetEvent(AssetEvent.Cause.UPDATE, asset))

        then: "hawkBit queries (404), creates, then uses the created target for info update"
        1 * service.targets.get(CONTROLLER_ID) >> Response.status(Response.Status.NOT_FOUND).build()
        1 * service.targets.create({ TargetCreateRequest[] targets ->
            targets.length == 1 && targets[0].securityToken() == null
        }) >> Response.ok(new Target[]{createdTarget}).build()
    }

    def "handleAssetChange with UPDATE cause logs warning when getTarget throws exception"() {
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

    def "createTarget with security token forwards token in create request"() {
        given: "a service with mocked targets client"
        def service = new TestableHawkbitFirmwareService()
        service.targets = Mock(HawkbitTargetsClient)
        def createdTarget = new Target(CONTROLLER_ID, null, null, "custom-token", null, null, null, null, null, null, null, null, null)

        def asset = Mock(Asset)
        asset.getId() >> CONTROLLER_ID
        asset.getAssetType() >> "test:asset:type"
        asset.getRealm() >> "master"

        when: "creating a target with a custom security token"
        def result = service.createTarget(asset, "custom-token")

        then: "the token is forwarded to hawkBit"
        1 * service.targets.create({ TargetCreateRequest[] targets ->
            targets.length == 1 &&
                    targets[0].controllerId() == CONTROLLER_ID &&
                    targets[0].securityToken() == "custom-token"
        }) >> Response.ok(new Target[]{createdTarget}).build()
        result == createdTarget
    }

    def "createUpdateTarget updates token when target exists"() {
        given: "a service with mocked targets client"
        def service = new TestableHawkbitFirmwareService()
        service.targets = Mock(HawkbitTargetsClient)
        def existingTarget = new Target(CONTROLLER_ID, null, null, "old-token", null, null, null, null, null, null, null, null, null)
        def updatedTarget = new Target(CONTROLLER_ID, null, null, "new-token", null, null, null, null, null, null, null, null, null)

        def asset = Mock(Asset)
        asset.getId() >> CONTROLLER_ID
        asset.getAssetType() >> "test:asset:type"
        asset.getRealm() >> "master"

        when: "creating or updating a target with a custom security token"
        def result = service.createUpdateTarget(asset, "new-token")

        then: "the existing target is updated with the new token"
        1 * service.targets.get(CONTROLLER_ID) >> Response.ok(existingTarget).build()
        1 * service.targets.update(CONTROLLER_ID, { TargetUpdateRequest target ->
            target.securityToken() == "new-token"
        }) >> Response.ok(updatedTarget).build()
        0 * service.targets.create(_)
        result == updatedTarget
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
