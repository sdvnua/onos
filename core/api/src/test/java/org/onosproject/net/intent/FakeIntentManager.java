/*
 * Copyright 2014 Open Networking Laboratory
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.onosproject.net.intent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fake implementation of the intent service to assist in developing tests of
 * the interface contract.
 */
public class FakeIntentManager implements TestableIntentService {

    private final Map<IntentId, Intent> intents = new HashMap<>();
    private final Map<IntentId, IntentState> intentStates = new HashMap<>();
    private final Map<IntentId, List<Intent>> installables = new HashMap<>();
    private final Set<IntentListener> listeners = new HashSet<>();

    private final Map<Class<? extends Intent>, IntentCompiler<? extends Intent>> compilers = new HashMap<>();
    private final Map<Class<? extends Intent>, IntentInstaller<? extends Intent>> installers
        = new HashMap<>();

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final List<IntentException> exceptions = new ArrayList<>();

    @Override
    public List<IntentException> getExceptions() {
        return exceptions;
    }

    // Provides an out-of-thread simulation of intent submit life-cycle
    private void executeSubmit(final Intent intent) {
        registerSubclassCompilerIfNeeded(intent);
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    executeCompilingPhase(intent);
                } catch (IntentException e) {
                    exceptions.add(e);
                }
            }
        });
    }

    // Provides an out-of-thread simulation of intent withdraw life-cycle
    private void executeWithdraw(final Intent intent) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    List<Intent> installable = getInstallable(intent.id());
                    executeWithdrawingPhase(intent, installable);
                } catch (IntentException e) {
                    exceptions.add(e);
                }

            }
        });
    }

    private <T extends Intent> IntentCompiler<T> getCompiler(T intent) {
        @SuppressWarnings("unchecked")
        IntentCompiler<T> compiler = (IntentCompiler<T>) compilers.get(intent.getClass());
        if (compiler == null) {
            throw new IntentException("no compiler for class " + intent.getClass());
        }
        return compiler;
    }

    private <T extends Intent> IntentInstaller<T> getInstaller(T intent) {
        @SuppressWarnings("unchecked")
        IntentInstaller<T> installer = (IntentInstaller<T>) installers.get(intent
                .getClass());
        if (installer == null) {
            throw new IntentException("no installer for class " + intent.getClass());
        }
        return installer;
    }

    private <T extends Intent> void executeCompilingPhase(T intent) {
        setState(intent, IntentState.COMPILING);
        try {
            // For the fake, we compile using a single level pass
            List<Intent> installable = new ArrayList<>();
            for (Intent compiled : getCompiler(intent).compile(intent, null, null)) {
                installable.add((Intent) compiled);
            }
            executeInstallingPhase(intent, installable);

        } catch (IntentException e) {
            setState(intent, IntentState.FAILED);
            dispatch(new IntentEvent(IntentEvent.Type.FAILED, intent));
        }
    }

    private void executeInstallingPhase(Intent intent,
                                        List<Intent> installable) {
        setState(intent, IntentState.INSTALLING);
        try {
            for (Intent ii : installable) {
                registerSubclassInstallerIfNeeded(ii);
                getInstaller(ii).install(ii);
            }
            setState(intent, IntentState.INSTALLED);
            putInstallable(intent.id(), installable);
            dispatch(new IntentEvent(IntentEvent.Type.INSTALLED, intent));

        } catch (IntentException e) {
            setState(intent, IntentState.FAILED);
            dispatch(new IntentEvent(IntentEvent.Type.FAILED, intent));
        }
    }

    private void executeWithdrawingPhase(Intent intent,
                                         List<Intent> installable) {
        setState(intent, IntentState.WITHDRAWING);
        try {
            for (Intent ii : installable) {
                getInstaller(ii).uninstall(ii);
            }
            removeInstallable(intent.id());
            setState(intent, IntentState.WITHDRAWN);
            dispatch(new IntentEvent(IntentEvent.Type.WITHDRAWN, intent));
        } catch (IntentException e) {
            // FIXME: Rework this to always go from WITHDRAWING to WITHDRAWN!
            setState(intent, IntentState.FAILED);
            dispatch(new IntentEvent(IntentEvent.Type.FAILED, intent));
        }
    }

    // Sets the internal state for the given intent and dispatches an event
    private void setState(Intent intent, IntentState state) {
        intentStates.put(intent.id(), state);
    }

    private void putInstallable(IntentId id, List<Intent> installable) {
        installables.put(id, installable);
    }

    private void removeInstallable(IntentId id) {
        installables.remove(id);
    }

    private List<Intent> getInstallable(IntentId id) {
        List<Intent> installable = installables.get(id);
        if (installable != null) {
            return installable;
        } else {
            return Collections.emptyList();
        }
    }

    @Override
    public void submit(Intent intent) {
        intents.put(intent.id(), intent);
        setState(intent, IntentState.INSTALL_REQ);
        dispatch(new IntentEvent(IntentEvent.Type.INSTALL_REQ, intent));
        executeSubmit(intent);
    }

    @Override
    public void withdraw(Intent intent) {
        intents.remove(intent.id());
        executeWithdraw(intent);
    }

    @Override
    public void replace(IntentId oldIntentId, Intent newIntent) {
        // TODO: implement later
    }

    @Override
    public Set<Intent> getIntents() {
        return Collections.unmodifiableSet(new HashSet<>(intents.values()));
    }

    @Override
    public long getIntentCount() {
        return intents.size();
    }

    @Override
    public Intent getIntent(IntentId id) {
        return intents.get(id);
    }

    @Override
    public IntentState getIntentState(IntentId id) {
        return intentStates.get(id);
    }

    @Override
    public List<Intent> getInstallableIntents(IntentId intentId) {
        return installables.get(intentId);
    }

    @Override
    public void addListener(IntentListener listener) {
        listeners.add(listener);
    }

    @Override
    public void removeListener(IntentListener listener) {
        listeners.remove(listener);
    }

    private void dispatch(IntentEvent event) {
        for (IntentListener listener : listeners) {
            listener.event(event);
        }
    }

    @Override
    public <T extends Intent> void registerCompiler(Class<T> cls,
            IntentCompiler<T> compiler) {
        compilers.put(cls, compiler);
    }

    @Override
    public <T extends Intent> void unregisterCompiler(Class<T> cls) {
        compilers.remove(cls);
    }

    @Override
    public Map<Class<? extends Intent>, IntentCompiler<? extends Intent>> getCompilers() {
        return Collections.unmodifiableMap(compilers);
    }

    @Override
    public <T extends Intent> void registerInstaller(Class<T> cls,
            IntentInstaller<T> installer) {
        installers.put(cls, installer);
    }

    @Override
    public <T extends Intent> void unregisterInstaller(Class<T> cls) {
        installers.remove(cls);
    }

    @Override
    public Map<Class<? extends Intent>,
    IntentInstaller<? extends Intent>> getInstallers() {
        return Collections.unmodifiableMap(installers);
    }

    private void registerSubclassCompilerIfNeeded(Intent intent) {
        if (!compilers.containsKey(intent.getClass())) {
            Class<?> cls = intent.getClass();
            while (cls != Object.class) {
                // As long as we're within the Intent class descendants
                if (Intent.class.isAssignableFrom(cls)) {
                    IntentCompiler<?> compiler = compilers.get(cls);
                    if (compiler != null) {
                        compilers.put(intent.getClass(), compiler);
                        return;
                    }
                }
                cls = cls.getSuperclass();
            }
        }
    }

    private void registerSubclassInstallerIfNeeded(Intent intent) {
        if (!installers.containsKey(intent.getClass())) {
            Class<?> cls = intent.getClass();
            while (cls != Object.class) {
                // As long as we're within the Intent class
                // descendants
                if (Intent.class.isAssignableFrom(cls)) {
                    IntentInstaller<?> installer = installers.get(cls);
                    if (installer != null) {
                        installers.put(intent.getClass(), installer);
                        return;
                    }
                }
                cls = cls.getSuperclass();
            }
        }
    }

}
