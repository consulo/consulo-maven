/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import consulo.application.ApplicationManager;
import consulo.codeEditor.EditorFactory;
import consulo.codeEditor.event.CaretAdapter;
import consulo.codeEditor.event.CaretEvent;
import consulo.codeEditor.event.EditorEventMulticaster;
import consulo.disposer.Disposable;
import consulo.document.event.DocumentAdapter;
import consulo.document.event.DocumentEvent;
import consulo.logging.Logger;
import consulo.module.content.layer.event.ModuleRootEvent;
import consulo.module.content.layer.event.ModuleRootListener;
import consulo.project.Project;
import consulo.ui.UIAccess;
import consulo.ui.event.ModalityStateListener;
import consulo.ui.ex.awt.util.MergingUpdateQueue;
import consulo.ui.ex.awt.util.Update;

import javax.annotation.Nonnull;
import javax.swing.*;
import java.util.concurrent.atomic.AtomicInteger;

public class MavenMergingUpdateQueue extends MergingUpdateQueue {
    private static final Logger LOG = Logger.getInstance(MavenMergingUpdateQueue.class);

    private final AtomicInteger mySuspendCounter = new AtomicInteger(0);

    public MavenMergingUpdateQueue(String name, int mergingTimeSpan, boolean isActive, Disposable parent) {
        this(name, mergingTimeSpan, isActive, ANY_COMPONENT, parent);
    }

    public MavenMergingUpdateQueue(
        String name,
        int mergingTimeSpan,
        boolean isActive,
        JComponent modalityStateComponent,
        Disposable parent
    ) {
        super(name, mergingTimeSpan, isActive, modalityStateComponent, parent, null, false);
    }

    @Override
    public void queue(@Nonnull Update update) {
        boolean passThrough = false;
        if (ApplicationManager.getApplication().isUnitTestMode()) {
            passThrough = isPassThrough();
        }
        else if (MavenUtil.isNoBackgroundMode()) {
            passThrough = true;
        }

        if (passThrough) {
            update.run();
            return;
        }
        super.queue(update);
    }

    public void makeUserAware(final Project project) {
        EditorEventMulticaster multicaster = EditorFactory.getInstance().getEventMulticaster();

        multicaster.addCaretListener(new CaretAdapter() {
            @Override
            public void caretPositionChanged(CaretEvent e) {
                MavenMergingUpdateQueue.this.restartTimer();
            }
        }, this);

        multicaster.addDocumentListener(new DocumentAdapter() {
            @Override
            public void documentChanged(DocumentEvent event) {
                MavenMergingUpdateQueue.this.restartTimer();
            }
        }, this);

        project.getMessageBus().connect(this).subscribe(ModuleRootListener.class, new ModuleRootListener() {
            int beforeCalled;

            @Override
            public void beforeRootsChange(ModuleRootEvent event) {
                if (beforeCalled++ == 0) {
                    suspend();
                }
            }

            @Override
            public void rootsChanged(ModuleRootEvent event) {
                if (beforeCalled == 0) {
                    return; // This may occur if listener has been added between beforeRootsChange() and rootsChanged() calls.
                }

                if (--beforeCalled == 0) {
                    resume();
                    MavenMergingUpdateQueue.this.restartTimer();
                }
            }
        });
    }

    public void makeModalAware(Project project) {
        MavenUtil.invokeLater(
            project,
            new Runnable() {
                @Override
                public void run() {
                    final ModalityStateListener listener = new ModalityStateListener() {
                        @Override
                        public void beforeModalityStateChanged(boolean entering, @Nonnull Object o) {
                            if (entering) {
                                suspend();
                            }
                            else {
                                resume();
                            }
                        }
                    };
                    UIAccess.addModalityStateListener(listener, MavenMergingUpdateQueue.this);
                    if (MavenUtil.isInModalContext()) {
                        suspend();
                    }
                }
            }
        );
    }

    @Override
    public void suspend() {
        if (mySuspendCounter.incrementAndGet() == 1) {
            super.suspend();
        }
    }

    @Override
    public void resume() {
        int c = mySuspendCounter.decrementAndGet();
        if (c <= 0) {
            if (c < 0) {
                mySuspendCounter.set(0);
                LOG.warn("Invalid suspend counter state", new Exception());
            }

            super.resume();
        }
    }
}
