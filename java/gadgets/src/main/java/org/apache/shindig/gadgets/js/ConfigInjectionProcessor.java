/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License.
 */
package org.apache.shindig.gadgets.js;

import org.apache.shindig.common.JsonSerializer;
import org.apache.shindig.gadgets.GadgetContext;
import org.apache.shindig.gadgets.RenderingContext;
import org.apache.shindig.gadgets.config.ConfigProcessor;
import org.apache.shindig.gadgets.features.FeatureRegistry;
import org.apache.shindig.gadgets.uri.JsUriManager.JsUri;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.inject.Inject;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ConfigInjectionProcessor implements JsProcessor {
  private static final String CONFIG_INIT_ID = "[config-injection]";
  @VisibleForTesting
  static final String GADGETS_FEATURES_KEY = "gadgets.features";
  @VisibleForTesting
  static final String CONFIG_INIT_TPL = "gadgets.config.init(%s);\n";
  @VisibleForTesting
  static final String GLOBAL_CONFIG_KEY_TPL = "window['___cfg']=%s;\n";
  @VisibleForTesting
  static final String CONFIG_FEATURE = "core.config.base";

  private final FeatureRegistry registry;
  private final ConfigProcessor configProcessor;
  
  @Inject
  public ConfigInjectionProcessor(
      FeatureRegistry registry,
      ConfigProcessor configProcessor) {
    this.registry = registry;
    this.configProcessor = configProcessor;
  }

  public boolean process(JsRequest request, JsResponseBuilder builder) {
    JsUri jsUri = request.getJsUri();
    GadgetContext ctx = new JsGadgetContext(jsUri);

    // Append gadgets.config initialization if not in standard gadget mode.
    if (ctx.getRenderingContext() != RenderingContext.GADGET) {
      List<String> allReq = registry.getFeatures(jsUri.getLibs());
      Collection<String> loaded = jsUri.getLoadedLibs();
      
      // Only inject config for features not already present and configured.
      List<String> newReq = subtractCollection(allReq, loaded);
      
      Map<String, Object> config = configProcessor.getConfig(
          ctx.getContainer(), newReq, request.getHost(), null);
      if (!config.isEmpty()) {
        String configJson = JsonSerializer.serialize(config);
        if (allReq.contains(CONFIG_FEATURE) || loaded.contains(CONFIG_FEATURE)) {
          // config lib is present: pass it data
          builder.appendJs(String.format(CONFIG_INIT_TPL, configJson), CONFIG_INIT_ID);
        } else {
          // config lib not available: use global variable
          builder.appendJs(String.format(GLOBAL_CONFIG_KEY_TPL, configJson), CONFIG_INIT_ID);
        }
      }
    }
    return true;
  }
  
  private List<String> subtractCollection(Collection<String> root, Collection<String> subtracted) {
    Set<String> result = Sets.newHashSet(root);
    result.removeAll(subtracted);
    return Lists.newArrayList(result);
  }

}
