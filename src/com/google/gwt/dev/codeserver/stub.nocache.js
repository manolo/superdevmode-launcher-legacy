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

/**
 * This startup script is used when we run superdevmode from an app server.
 * 
 * The main goal is to avoid installing bookmarkers to load and recompile
 * the application.
 */
(function($wnd, $doc){
  if (!('sessionStorage' in $wnd)) {
    $wnd.alert('Unable to load Super Dev Mode version of __MODULE_NAME__ because this browser does not support sessionStorage');
    return;
  }

  //Because we don't import properties.js, we set some needed variables.
  $wnd.__gwt_activeModules = [];
  $wnd.__gwt_activeModules['__MODULE_NAME__'] = {
    'moduleName' : '__MODULE_NAME__',
    'bindings' : function() {
      return {};
    }
  };

  // Reuse compute script base
  __COMPUTE_SCRIPT_BASE__;

  // doc.head does not exist in IE8
  var head = $doc.head || $doc.getElementsByTagName('head')[0];

  // Quick way to compute the user.agent for most common browsers.
  // This makes the first compilation run faster, for other browsers
  // we compile all permutations but only the first time.
  var ua = $wnd.navigator.userAgent.toLowerCase();
  ua = /webkit/.test(ua) ? 'safari' : /gecko/.test(ua) ? 'gecko1_8' : '';

  // We use a different key for each module so that we can turn on dev mode
  // independently for each.
  var devModeHookKey = '__gwtDevModeHook:__MODULE_NAME__';
  var devModeSessionKey = '__gwtDevModeSession:__MODULE_NAME__';

  // Compute the superdevmode url, so as the user does not need bookmarkers
  var hostName = $wnd.location.hostname;
  var codsrvUrl = 'http://' + hostName + ':__SUPERDEV_PORT__';
  var codsrvNocacheUrl = codsrvUrl + '/__MODULE_NAME__/__MODULE_NAME__.nocache.js';
  var codsrvCompileUrl = codsrvUrl + '/recompile/__MODULE_NAME__?user.agent=' + ua + "&_callback=_compile_callback";

  // Save supder-devmode url in session
  $wnd.sessionStorage[devModeHookKey] = codsrvNocacheUrl;
  // Save user.agent in session
  if (ua) {
    $wnd.sessionStorage[devModeSessionKey] = 'user.agent=' + ua + '&';
  }

  // Set bookmarklet params in window
  $wnd.__gwt_bookmarklet_params = {'server_url': codsrvUrl};
  // Save the original module base. (Returned by GWT.getModuleBaseURL.)
  $wnd[devModeHookKey + ':moduleBase'] = computeScriptBase();

  // Needed in the real nocache.js logic
  $wnd.__gwt_activeModules['__MODULE_NAME__'].canRedirect = true;
  $wnd.__gwt_activeModules['__MODULE_NAME__'].superdevmode = true;

  // Insert the superdevmode nocache script in the first position of the head
  var devModeScript = $doc.createElement('script');
  devModeScript.src = codsrvNocacheUrl;
  head.insertBefore(devModeScript, head.firstElementChild || head.children[0]);
  
  // EXPERIMENTAL (for discussion during review process)
  // Show a link in a corner for recompiling the app.
  // The user can remove this: .gwt-superdev-compile{display:none}
  // Goal: no need to install bookmarker for host:port/module
  var compileDiv = $doc.createElement('div');
  compileDiv.innerHTML = "\u21bb";
  compileDiv.title = 'Compile module: __MODULE_NAME__';
  compileDiv.className = 'gwt-superdev-compile';
  compileDiv.style.position = 'fixed';
  compileDiv.style.right = '2px';
  compileDiv.style.bottom = '2px';
  compileDiv.style.cursor = 'pointer';
  compileDiv.style.fontSize = '24px';
  compileDiv.style.fontFamily = 'arial';
  compileDiv.style.zindex = 2147483646;
  compileDiv.onclick = function() {
    compile();
  };
  // defer so as the body is ready
  setTimeout(function(){
    $doc.body.appendChild(compileDiv);
  }, 1);

  // EXPERIMENTAL (for discussion during review process)
  // Configure Ctrl-K for recompiling the app
  // Goal: no need to install bookmarker for host:port/module
  $doc.addEventListener('keyup', function(e){
    if (e.ctrlKey && e.keyCode == 89) {
      compile();
    }
  }, false);

  // Compile this module
  function compile() {
    // Notice that we are compiling
    $doc.body.style.opacity = 0.4;
    // Insert the jsonp script to compile
    var compileScript = $doc.createElement('script');
    compileScript.src = codsrvCompileUrl;
    head.appendChild(compileScript);
  }

  // Compile callback
  $wnd._compile_callback = function(r) {
    $doc.body.style.opacity = 1;
    if (r && r.status) {
      if (r.status == 'ok')
        $wnd.location.reload();
      else
        $wnd.alert(r.status);
    }
  };

  // Run this block after the app has been loaded.
  setTimeout(function(){
    // Maintaining the hook key in session can cause problems
    // if we try to run classic code server.
    $wnd.sessionStorage.removeItem(devModeHookKey);

    // Re-attach because sometimes app clears the dom
    $doc.body.appendChild(compileDiv);
  }, 2000);

})(window, document);

