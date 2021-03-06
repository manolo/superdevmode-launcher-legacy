/*
 * Copyright 2014 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.gwt.dev.shell;

import com.google.gwt.core.ext.TreeLogger;
import com.google.gwt.core.ext.TreeLogger.Type;
import com.google.gwt.core.ext.UnableToCompleteException;
import com.google.gwt.core.ext.linker.ArtifactSet;
import com.google.gwt.core.ext.linker.impl.StandardLinkerContext;
import com.google.gwt.dev.DevMode.HostedModeOptions;
import com.google.gwt.dev.cfg.ModuleDef;
import com.google.gwt.util.tools.Utility;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.ServerSocket;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Starts a superdev-mode codeserver.
 */
public class SuperDevListener implements CodeServerListener {

  private int codSvrPort;
  private Thread listenThread;
  private TreeLogger logger;
  private HostedModeOptions options;

  /**
   * Listens for new connections from browsers.
   */
  public SuperDevListener(TreeLogger treeLogger, HostedModeOptions options) {
    List<String> args = new ArrayList<String>();
    this.logger = treeLogger;
    this.options = options;

    codSvrPort = options.getCodeServerPort();

    if (codSvrPort < 0 || codSvrPort == 9997) {
      codSvrPort = 9876;
    } else if (codSvrPort == 0) {
      try {
        ServerSocket serverSocket = new ServerSocket(0);
        codSvrPort = serverSocket.getLocalPort();
        serverSocket.close();
      } catch (IOException e) {
        logger.log(TreeLogger.ERROR, "Unable to get an unnused port.");
        throw new RuntimeException(e);
      }
    }

    // Let RemoteServiceServlet know the location of RPC serialization policies (issue #8850)
    System.setProperty("gwt.codeserver.port", String.valueOf(codSvrPort));

    try {
      Class<?> clazz = Class.forName("com.google.gwt.dev.codeserver.Options");
      for (Class<?> c : clazz.getDeclaredClasses()) {
        if ("NoPrecompileFlag".equals(c.getSimpleName())) {
          args.add("-noprecompile");
          break;
        }
      }
    } catch (ClassNotFoundException e) {
    }

    args.add("-port");
    args.add(String.valueOf(codSvrPort));
    if (options.getBindAddress() != null) {
      args.add("-bindAddress");
      args.add(options.getBindAddress());
    }
    if (options.getWorkDir() != null) {
      args.add("-workDir");
      args.add(String.valueOf(options.getWorkDir()));
    }
    for (String mod : options.getModuleNames()) {
      args.add(mod);
    }

    final String[] codServerArgs = args.toArray(new String[0]);

    logger.log(Type.INFO, "Runing CodeServer with parameters: " + args);

    try {
      // Using reflection so as we don't create a circular dependency between
      // dev.jar && codeserver.jar
      Class<?> clazz = Class.forName("com.google.gwt.dev.codeserver.CodeServer");
      final Method method = clazz.getMethod("main", String[].class);

      listenThread = new Thread() {
        public void run() {
          try {
            method.invoke(null, new Object[] {codServerArgs});
          } catch (Exception e) {
            logger.log(TreeLogger.ERROR, "Unable to run superdev codeServer.", e);
          }
        }
      };
      listenThread.setName("SuperDevMode code server listener");
      listenThread.setDaemon(true);
    } catch (ClassNotFoundException e) {
      logger.log(TreeLogger.ERROR,
          "Unable to run superdev codeServer, verify that 'codeserver.jar' is in your classpath.");
      throw new RuntimeException(e);
    } catch (Exception e) {
      logger.log(TreeLogger.ERROR, "Unable to run superdev codeServer.", e);
      throw new RuntimeException(e);
    }
  }

  @Override
  public int getSocketPort() {
    return codSvrPort;
  }

  @Override
  public URL processUrl(String url) throws UnableToCompleteException {
    try {
      return new URL(url);
    } catch (MalformedURLException e) {
      logger.log(TreeLogger.ERROR, "Invalid URL " + url, e);
      throw new UnableToCompleteException();
    }
  }

  @Override
  public void produceBrowserOutput(StandardLinkerContext linkerStack, ArtifactSet artifacts,
      ModuleDef module, boolean isRelink) throws UnableToCompleteException {
    try {
      String computeScriptBase =
          Utility.getFileFromClassPath("com/google/gwt/dev/codeserver/computeScriptBase.js");
      String contents =
          Utility.getFileFromClassPath("com/google/gwt/dev/codeserver/stub.nocache.js");

      contents = contents.replace("__COMPUTE_SCRIPT_BASE__", computeScriptBase);

      File file =
          new File(options.getWarDir() + "/" + module.getName() + "/" + module.getName()
              + ".nocache.js");
      file.deleteOnExit();
      file.getParentFile().mkdirs();
      System.out.println("Creating nocache file: " + file.getAbsolutePath());

      Map<String, String> replacements = new HashMap<String, String>();
      replacements.put("__MODULE_NAME__", module.getName());
      replacements.put("__SUPERDEV_PORT__", "" + codSvrPort);

      Utility.writeTemplateFile(file, contents, replacements);

      // TODO(manolo): make codeserver accept multiple user.agent separated by comma
      // and empty _callback parameter returning json.
      String recompileUrl =
          "http://" + options.getConnectAddress() + ":" + codSvrPort + "/recompile/"
              + module.getName() + "?_callback=cback&user.agent=safari,gecko1_8,ie8,ie9,ie10";

      System.out.println("To compile the module '" + module.getName()
          + "' , visit:\n " + recompileUrl);
    } catch (IOException e) {
      logger.log(TreeLogger.ERROR, "Unable to create nocache script ", e);
      throw new UnableToCompleteException();
    }
  }

  @Override
  public void setIgnoreRemoteDeath(boolean b) {
  }

  @Override
  public void start() {
    if (listenThread != null) {
      listenThread.start();
    }
  }
}
