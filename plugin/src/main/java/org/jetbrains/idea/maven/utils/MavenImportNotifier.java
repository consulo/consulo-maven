/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.maven.utils;

import consulo.annotation.component.ComponentScope;
import consulo.annotation.component.ServiceAPI;
import consulo.annotation.component.ServiceImpl;
import consulo.disposer.Disposable;
import consulo.maven.MavenNotificationGroup;
import consulo.project.Project;
import consulo.project.ui.notification.Notification;
import consulo.project.ui.notification.NotificationType;
import consulo.project.ui.notification.Notifications;
import consulo.ui.ex.awt.util.MergingUpdateQueue;
import consulo.ui.ex.awt.util.Update;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.jetbrains.idea.maven.localize.MavenProjectLocalize;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import javax.swing.event.HyperlinkEvent;

@ServiceAPI(value = ComponentScope.PROJECT, lazy = false)
@ServiceImpl
@Singleton
public class MavenImportNotifier extends MavenSimpleProjectComponent implements Disposable {
    private MavenProjectsManager myMavenProjectsManager;
    private MergingUpdateQueue myUpdatesQueue;

    private Notification myNotification;

    @Inject
    public MavenImportNotifier(Project p, MavenProjectsManager mavenProjectsManager) {
        super(p);

        if (!isNormalProject()) {
            return;
        }

        myMavenProjectsManager = mavenProjectsManager;

        myUpdatesQueue = new MergingUpdateQueue(
            "MavenImportNotifier",
            500,
            false,
            MergingUpdateQueue.ANY_COMPONENT,
            myProject
        );

        myMavenProjectsManager.addManagerListener(new MavenProjectsManager.Listener() {
            @Override
            public void activated() {
                init();
            }

            @Override
            public void projectsScheduled() {
                scheduleUpdate(false);
            }

            @Override
            public void importAndResolveScheduled() {
                scheduleUpdate(true);
            }
        });
    }

    private void init() {
        myUpdatesQueue.activate();
    }

    @Override
    public void dispose() {
        if (myNotification != null) {
            myNotification.expire();
        }
    }

    private void scheduleUpdate(final boolean close) {
        myUpdatesQueue.queue(new Update(myUpdatesQueue) {
            @Override
            public void run() {
                doUpdateNotifications(close);
            }
        });
    }

    private void doUpdateNotifications(boolean close) {
        if (close) {
            if (myNotification == null) {
                return;
            }

            myNotification.expire();
            myNotification = null;
        }
        else {
            if (myNotification != null && !myNotification.isExpired()) {
                return;
            }

            myNotification = new Notification(
                MavenNotificationGroup.IMPORT,
                MavenProjectLocalize.mavenProjectChanged().get(),
                "<a href='reimport'>" + MavenProjectLocalize.mavenProjectImportchanged() + "</a> " +
                    "<a href='autoImport'>" + MavenProjectLocalize.mavenProjectEnableautoimport() + "</a>",
                NotificationType.INFORMATION,
                (notification, event) -> {
                    if (event.getEventType() != HyperlinkEvent.EventType.ACTIVATED) {
                        return;
                    }

                    if (event.getDescription().equals("reimport")) {
                        myMavenProjectsManager.scheduleImportAndResolve();
                    }
                    if (event.getDescription().equals("autoImport")) {
                        myMavenProjectsManager.getImportingSettings().setImportAutomatically(true);
                    }
                    notification.expire();
                    myNotification = null;
                }
            );

            Notifications.Bus.notify(myNotification, myProject);
        }
    }
}
