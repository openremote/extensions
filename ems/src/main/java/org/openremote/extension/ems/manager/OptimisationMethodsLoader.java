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

import static org.openremote.model.syslog.SyslogCategory.DATA;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Objects;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.logging.Logger;
import org.openremote.extension.ems.manager.optimisationMethods.OptimisationMethod;
import org.openremote.model.syslog.SyslogCategory;

public class OptimisationMethodsLoader {
  protected static final Logger LOG =
      SyslogCategory.getLogger(DATA, OptimisationMethodsLoader.class.getName());

  private final String OPTIMISATION_METHODS_PACKAGE_NAME =
      OptimisationMethodsLoader.class.getPackage().getName() + ".optimisationMethods";

  protected void runOptimisationMethod(
      String currentOptimisationMethodName, String energyOptimisationAssetId, Services services) {
    List<OptimisationMethod> optimisationMethods = loadOptimisationMethods();

    for (OptimisationMethod optimisationMethod : optimisationMethods) {
      String classNameCurrent =
          OPTIMISATION_METHODS_PACKAGE_NAME + "." + currentOptimisationMethodName;
      String classNameMethod = optimisationMethod.getClass().getName();

      if (classNameMethod.equals(classNameCurrent)) {
        optimisationMethod.execute(energyOptimisationAssetId, services);
        return;
      }
    }
    if (!currentOptimisationMethodName.equals("None")) {
      LOG.info(
          String.format(
              "assetId='%s'; Optimisation method '%s' not found",
              energyOptimisationAssetId, currentOptimisationMethodName));
    }
  }

  private List<OptimisationMethod> loadOptimisationMethods() {
    List<OptimisationMethod> optimisationMethods = new ArrayList<>();

    try {
      // Get the package path
      String packagePath = OPTIMISATION_METHODS_PACKAGE_NAME.replace('.', '/');
      URL packageURL = Thread.currentThread().getContextClassLoader().getResource(packagePath);

      if (packageURL == null) {
        LOG.info(
            String.format(
                "Package not found; packageName='%s'", OPTIMISATION_METHODS_PACKAGE_NAME));
        return optimisationMethods;
      }

      if (packageURL.getProtocol().equals("file")) {
        // Handle file URL
        File directory = new File(packageURL.toURI());

        if (!directory.exists() || !directory.isDirectory()) {
          LOG.info(
              String.format(
                  "Invalid package directory; packageName='%s', directoryPath='%s'",
                  OPTIMISATION_METHODS_PACKAGE_NAME, directory.getAbsolutePath()));
          return optimisationMethods;
        }

        // Check if the file is a class file
        for (String fileName : Objects.requireNonNull(directory.list())) {
          if (fileName.endsWith(".class")) {
            String className =
                OPTIMISATION_METHODS_PACKAGE_NAME + "." + fileName.replace(".class", "");
            Class<?> clazz = Class.forName(className);

            if (OptimisationMethod.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
              optimisationMethods.add(
                  (OptimisationMethod) clazz.getDeclaredConstructor().newInstance());
            }
          }
        }
      } else if (packageURL.getProtocol().equals("jar")) {
        // Handle the case where the resources are in a JAR file
        String jarFilePath = packageURL.getPath().substring(5, packageURL.getPath().indexOf("!"));
        JarFile jarFile = new JarFile(jarFilePath);
        Enumeration<JarEntry> entries = jarFile.entries();

        while (entries.hasMoreElements()) {
          JarEntry entry = entries.nextElement();

          // Check if the entry is a class file
          if (entry.getName().endsWith(".class") && entry.getName().startsWith(packagePath)) {
            String className = entry.getName().replace('/', '.').replace(".class", "");
            Class<?> clazz = Class.forName(className);

            if (OptimisationMethod.class.isAssignableFrom(clazz) && !clazz.isInterface()) {
              optimisationMethods.add(
                  (OptimisationMethod) clazz.getDeclaredConstructor().newInstance());
            }
          }
        }

        jarFile.close();
      } else {
        LOG.info(
            String.format("Unknown URL protocol; protocolName='%s'", packageURL.getProtocol()));
      }

    } catch (Exception e) {
      LOG.info(
          String.format(
              "An exception occurred during loading of optimisation methods; Exception: %s", e));
    }

    return optimisationMethods;
  }
}
