/*
 * The MIT License
 *
 * Copyright (c) 2011-2012, CloudBees, Inc., Stephen Connolly.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package com.cloudbees.jenkins.plugins.freeplugins;

import hudson.BulkChange;
import hudson.Extension;
import hudson.Plugin;
import hudson.PluginWrapper;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.lifecycle.RestartNotSupportedException;
import hudson.model.Hudson;
import hudson.model.UpdateCenter;
import hudson.model.UpdateSite;
import hudson.security.ACL;
import hudson.triggers.SafeTimerTask;
import hudson.triggers.Trigger;
import hudson.util.PersistedList;
import hudson.util.TimeUnit2;
import hudson.util.VersionNumber;
import jenkins.model.Jenkins;
import org.acegisecurity.context.SecurityContext;
import org.acegisecurity.context.SecurityContextHolder;
import org.jvnet.hudson.reactor.Milestone;
import org.jvnet.localizer.Localizable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Removed the custom update site.
 */
public class PluginImpl extends Plugin {

    /**
     * Our logger.
     */
    private static final Logger LOGGER = Logger.getLogger(PluginImpl.class.getName());

    @Initializer(after = InitMilestone.EXTENSIONS_AUGMENTED, attains = "cloudbees-update-center-configured")
    public static void removeUpdateCenter() throws Exception {
        LOGGER.log(Level.FINE, "Checking that the CloudBees update center has been configured.");
        UpdateCenter updateCenter = Hudson.getInstance().getUpdateCenter();
        synchronized (updateCenter) {
            PersistedList<UpdateSite> sites = updateCenter.getSites();
            if (sites.isEmpty()) {
                // likely the list has not been loaded yet
                updateCenter.load();
                sites = updateCenter.getSites();
            }

            List<UpdateSite> forRemoval = new ArrayList<UpdateSite>();
            for (UpdateSite site : sites) {
                LOGGER.log(Level.FINEST, "Update site {0} class {1} url {2}",
                        new Object[]{site.getId(), site.getClass(), site.getUrl()});
                if (site instanceof CloudBeesUpdateSite) {
                    LOGGER.log(Level.FINE, "Found possible match:\n  class = {0}\n  url = {1}\n  id = {2}",
                            new Object[]{site.getClass().getName(), site.getUrl(), site.getId()});
                    forRemoval.add(site);
                }
            }

            // now make the changes if we have any to make
            LOGGER.log(Level.FINE, "Removing={0}", forRemoval);
            if (!forRemoval.isEmpty()) {
                BulkChange bc = new BulkChange(updateCenter);
                try {
                    for (UpdateSite site : forRemoval) {
                        LOGGER.info("Removing legacy CloudBees Update Center from list of update centers");
                        sites.remove(site);
                    }
                    if (sites.isEmpty()) {
                        LOGGER.info("Adding Default Update Center to list of update centers as it was missing");
                        sites.add(new UpdateSite("default",
                                System.getProperty(UpdateCenter.class.getName() + ".updateCenterUrl",
                                        "http://updates.jenkins-ci.org/")
                                        + "update-center.json"));
                    }
                } finally {
                    bc.commit();
                }
            }
        }
    }

    static {
        UpdateCenter.XSTREAM.alias("cloudbees", CloudBeesUpdateSite.class);
    }
}
