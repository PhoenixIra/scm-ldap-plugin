/*
 * MIT License
 *
 * Copyright (c) 2020-present Cloudogu GmbH and Contributors
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package sonia.scm.auth.ldap;

import com.google.common.annotations.VisibleForTesting;
import sonia.scm.store.ConfigurationStore;
import sonia.scm.store.ConfigurationStoreFactory;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

@Singleton
public class LdapConfigStore implements Provider<LdapConfig> {

  private final ConfigurationStore<LdapConfig> configurationStore;

  @Inject
  public LdapConfigStore(ConfigurationStoreFactory configurationStoreFactory) {
    this(configurationStoreFactory.withType(LdapConfig.class).withName("ldap").build());
  }

  @VisibleForTesting
  LdapConfigStore(ConfigurationStore<LdapConfig> configurationStore) {
    this.configurationStore = configurationStore;
  }

  public LdapConfig get() {
    return configurationStore.getOptional().orElse(new LdapConfig());
  }

  public void set(LdapConfig config) {
    configurationStore.set(config);
  }
}
