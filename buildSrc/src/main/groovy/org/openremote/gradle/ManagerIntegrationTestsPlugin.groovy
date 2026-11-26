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
package org.openremote.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.testing.Test

/**
 * Runs test tasks that share the OpenRemote dev-testing stack one at a time within a Gradle
 * invocation. Manager integration tests reset the shared PostgreSQL schema, so concurrent tasks
 * can disrupt each other.
 *
 * <p>Apply this plugin in the {@code plugins} block of a subproject whose tests start the manager
 * against the shared stack.
 *
 * <pre>
 * plugins {
 *   id 'openremote.manager-integration-tests'
 * }
 * </pre>
 *
 * <p>Start PostgreSQL and Keycloak before running the project's usual test task, such as
 * {@code ./gradlew :ems:test}. Other Gradle tasks can still run in parallel.
 */
class ManagerIntegrationTestsPlugin implements Plugin<Project> {

  @Override
  void apply(Project project) {
    def managerTestStack = project.gradle.sharedServices.registerIfAbsent('managerTestStack', ManagerTestStack) {
      maxParallelUsages = 1
    }

    project.tasks.withType(Test).configureEach {
      usesService(managerTestStack)
    }
  }

  abstract static class ManagerTestStack implements BuildService<BuildServiceParameters.None> {}
}
