/**
 * Copyright (c) 2005-2013 by Appcelerator, Inc. All Rights Reserved.
 * Licensed under the terms of the Eclipse Public License (EPL).
 * Please see the license.txt included with this distribution for details.
 * Any modifications to this file must keep this entire header intact.
 */
/*
 * Created on 28/09/2005
 */
package com.python.pydev.analysis.additionalinfo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.python.pydev.core.FastBufferedReader;
import org.python.pydev.core.IInterpreterManager;
import org.python.pydev.core.IModule;
import org.python.pydev.core.IToken;
import org.python.pydev.core.MisconfigurationException;
import org.python.pydev.core.ModulesKey;
import org.python.pydev.core.ObjectsInternPool;
import org.python.pydev.core.ObjectsInternPool.ObjectsPoolMap;
import org.python.pydev.core.cache.CompleteIndexKey;
import org.python.pydev.core.cache.DiskCache;
import org.python.pydev.core.log.Log;
import org.python.pydev.editor.codecompletion.revisited.PyPublicTreeMap;
import org.python.pydev.editor.codecompletion.revisited.PythonPathHelper;
import org.python.pydev.editor.codecompletion.revisited.javaintegration.AbstractJavaClassModule;
import org.python.pydev.logging.DebugSettings;
import org.python.pydev.parser.jython.SimpleNode;
import org.python.pydev.parser.jython.ast.Name;
import org.python.pydev.parser.jython.ast.stmtType;
import org.python.pydev.parser.jython.ast.factory.AdapterPrefs;
import org.python.pydev.parser.jython.ast.factory.PyAstFactory;
import org.python.pydev.shared_core.callbacks.CallbackWithListeners;
import org.python.pydev.shared_core.io.FileUtils;
import org.python.pydev.shared_core.string.FastStringBuffer;
import org.python.pydev.shared_core.string.StringUtils;
import org.python.pydev.shared_core.structure.Tuple;
import org.python.pydev.shared_core.structure.Tuple3;
import org.python.pydev.ui.pythonpathconf.InterpreterInfo;

/**
 * Adds information on the modules being tracked.
 */
public abstract class AbstractAdditionalDependencyInfo extends AbstractAdditionalTokensInfo {

    public static boolean TESTING = false;

    public static final boolean DEBUG = false;

    /**
     * indexes all the names that are available
     *
     * Note that the key in the disk cache is the module name and each
     * module points to a Set<Strings>
     *
     * So the key is the module name and the value is a Set of the strings it contains.
     */
    public DiskCache completeIndex;

    private volatile IReferenceSearches referenceSearches;
    private final Object referenceSearchesLock = new Object();

    private IReferenceSearches getReferenceSearches() {
        if (referenceSearches == null) {
            synchronized (referenceSearchesLock) {
                if (referenceSearches == null) {
                    referenceSearches = new ReferenceSearchesLucene(this);
                    //referenceSearches = new ReferenceSearches(this);
                }
            }
        }
        return referenceSearches;
    }

    /**
     * default constructor
     * @throws MisconfigurationException
     */
    public AbstractAdditionalDependencyInfo() throws MisconfigurationException {
        init();
    }

    public AbstractAdditionalDependencyInfo(boolean callInit) throws MisconfigurationException {
        if (callInit) {
            init();
        }
    }

    /**
     * Initializes the internal DiskCache with the indexes.
     * @throws MisconfigurationException
     */
    protected void init() throws MisconfigurationException {
        File persistingFolder = getCompleteIndexPersistingFolder();

        completeIndex = new DiskCache(persistingFolder, ".v2_indexcache");
    }

    /**
     * @return a folder where the index should be persisted
     * @throws MisconfigurationException
     */
    protected File getCompleteIndexPersistingFolder() throws MisconfigurationException {
        File persistingFolder = getPersistingFolder();
        persistingFolder = new File(persistingFolder, "v2_indexcache");

        if (persistingFolder.exists()) {
            if (!persistingFolder.isDirectory()) {
                persistingFolder.delete();
            }
        }
        if (!persistingFolder.exists()) {
            persistingFolder.mkdirs();
        }
        return persistingFolder;
    }

    @Override
    public void clearAllInfo() {
        synchronized (lock) {
            super.clearAllInfo();
            try {
                completeIndex.clear();
            } catch (NullPointerException e) {
                //that's ok... because it might be called before actually having any values
            }
        }
    }

    /**
     * This is mostly for whitebox testing the updateKeysIfNeededAndSave. It'll called with a tuple containing
     * the keys added and the keys removed.
     */
    public static final CallbackWithListeners modulesAddedAndRemoved = new CallbackWithListeners(1);

    public void updateKeysIfNeededAndSave(PyPublicTreeMap<ModulesKey, ModulesKey> keysFound, InterpreterInfo info,
            IProgressMonitor monitor) {
        Map<CompleteIndexKey, CompleteIndexKey> keys = this.completeIndex.keys();

        ArrayList<ModulesKey> newKeys = new ArrayList<ModulesKey>();
        ArrayList<ModulesKey> removedKeys = new ArrayList<ModulesKey>();

        //temporary
        CompleteIndexKey tempKey = new CompleteIndexKey((ModulesKey) null);

        boolean isJython = info.getInterpreterType() == IInterpreterManager.INTERPRETER_TYPE_JYTHON;

        Iterator<ModulesKey> it = keysFound.values().iterator();
        while (it.hasNext()) {
            ModulesKey next = it.next();
            if (next.file != null) { //Can be a .pyd or a .py
                long lastModified = next.file.lastModified();
                if (lastModified != 0) {
                    tempKey.key = next;
                    CompleteIndexKey completeIndexKey = keys.get(tempKey);
                    if (completeIndexKey == null) {
                        newKeys.add(next);
                    } else {
                        if (completeIndexKey.lastModified != lastModified) {
                            //Just re-add it if the time changed!
                            newKeys.add(next);
                        }
                    }
                }
            } else { //at this point, it's always a compiled module (forced builtin), so, we can't check if it was modified (just re-add it).
                tempKey.key = next;
                CompleteIndexKey completeIndexKey = keys.get(tempKey);
                if (completeIndexKey == null) {
                    newKeys.add(next); //Only add if it's not there already.
                }
            }
        }

        Iterator<CompleteIndexKey> it2 = keys.values().iterator();
        while (it2.hasNext()) {
            CompleteIndexKey next = it2.next();
            if (!keysFound.containsKey(next.key)) {
                removedKeys.add(next.key);
            }
        }

        boolean hasNew = newKeys.size() != 0;
        boolean hasRemoved = removedKeys.size() != 0;
        modulesAddedAndRemoved.call(new Tuple(newKeys, removedKeys));

        Set<File> ignoreFiles = new HashSet<File>();

        if (hasNew) {
            FastStringBuffer buffer = new FastStringBuffer();
            int currI = 0;
            int total = newKeys.size();
            for (ModulesKey newKey : newKeys) {
                currI += 1;
                if (monitor.isCanceled()) {
                    return;
                }
                if (PythonPathHelper.canAddAstInfoForSourceModule(newKey)) {
                    buffer.clear().append("Indexing ").append(currI).append(" of ").append(total)
                            .append(" (source module): ").append(newKey.name).append("  (")
                            .append(currI).append(" of ").append(total).append(")");
                    try {
                        this.addAstInfo(newKey, false);
                    } catch (Exception e) {
                        Log.log(e);
                    }
                } else {
                    if (isJython && ignoreFiles.contains(newKey.file)) {
                        continue;
                    }
                    buffer.clear().append("Indexing ").append(currI).append(" of ").append(total)
                            .append(" (builtin module): ").append(newKey.name);
                    monitor.setTaskName(buffer.toString());
                    IModule builtinModule = info.getModulesManager().getModule(newKey.name,
                            info.getModulesManager().getNature(), true);
                    if (builtinModule != null) {
                        if (builtinModule instanceof AbstractJavaClassModule) {
                            if (newKey.file != null) {
                                ignoreFiles.add(newKey.file);
                            } else {
                                Log.log("Not expecting null file for java class module: " + newKey);
                            }
                            continue;
                        }
                        boolean removeFirst = keys.containsKey(newKey);
                        addAstForCompiledModule(builtinModule, info, newKey, removeFirst);
                    }
                }
            }
        }

        if (hasRemoved) {
            for (ModulesKey removedKey : removedKeys) {
                this.removeInfoFromModule(removedKey.name, false);
            }
        }

        if (hasNew || hasRemoved) {
            if (DebugSettings.DEBUG_INTERPRETER_AUTO_UPDATE) {
                Log.toLogFile(this,
                        StringUtils.format(
                                "Additional info modules. Added: %s Removed: %s", newKeys, removedKeys));
            }
            save();
        }
    }

    private void addAstForCompiledModule(IModule module, InterpreterInfo info, ModulesKey newKey, boolean removeFirst) {
        IToken[] globalTokens = module.getGlobalTokens();
        PyAstFactory astFactory = new PyAstFactory(new AdapterPrefs("\n", info.getModulesManager().getNature()));

        List<stmtType> body = new ArrayList<>(globalTokens.length);

        for (IToken token : globalTokens) {
            switch (token.getType()) {

                case IToken.TYPE_CLASS:
                    body.add(astFactory.createClassDef(token.getRepresentation()));
                    break;

                case IToken.TYPE_FUNCTION:
                    body.add(astFactory.createFunctionDef(token.getRepresentation()));
                    break;

                default:
                    Name attr = astFactory.createName(token.getRepresentation());
                    body.add(astFactory.createAssign(attr, attr)); //assign to itself just for generation purposes.
                    break;
            }
        }
        //System.out.println("Creating info for: " + module.getName());
        if (removeFirst) {
            removeInfoFromModule(newKey.name, false);
        }
        addAstInfo(astFactory.createModule(body), newKey, false);
    }

    static interface IBufferFiller {
        void fillBuffer(FastStringBuffer buf);
    }

    /**
     * Note: if it's a name with dots, we'll split it and search for each one.
     */
    @Override
    public List<ModulesKey> getModulesWithToken(IProject project, String token, IProgressMonitor monitor) {
        NullProgressMonitor nullMonitor = new NullProgressMonitor();
        if (monitor == null) {
            monitor = nullMonitor;
        }
        int length = token.length();
        if (token == null || length == 0) {
            return new ArrayList<>();
        }

        for (int i = 0; i < length; i++) {
            char c = token.charAt(i);
            if (!Character.isJavaIdentifierPart(c) && c != '.') {
                throw new RuntimeException(StringUtils.format(
                        "Token: %s is not a valid token to search for.", token));
            }
        }
        return getReferenceSearches().search(project, token, monitor);
    }

    protected abstract String getUIRepresentation();

    protected abstract Set<String> getPythonPathFolders();

    @Override
    public List<IInfo> addAstInfo(SimpleNode node, ModulesKey key, boolean generateDelta) {
        List<IInfo> addAstInfo = new ArrayList<IInfo>();
        if (node == null || key == null || key.name == null) {
            return addAstInfo;
        }
        try {
            synchronized (lock) {
                addAstInfo = super.addAstInfo(node, key, generateDelta);

                CompleteIndexKey completeIndexKey = new CompleteIndexKey(key);
                if (key.file != null) {
                    completeIndexKey.lastModified = key.file.lastModified();
                }
                completeIndex.add(completeIndexKey);

            }
        } catch (Exception e) {
            Log.log(e);
        }
        return addAstInfo;
    }

    @Override
    public void removeInfoFromModule(String moduleName, boolean generateDelta) {
        synchronized (lock) {
            if (moduleName == null) {
                throw new AssertionError("The module name may not be null.");
            }
            completeIndex.remove(new CompleteIndexKey(moduleName));
            super.removeInfoFromModule(moduleName, generateDelta);
        }
    }

    @Override
    protected void saveTo(OutputStreamWriter writer, FastStringBuffer tempBuf, File pathToSave) throws IOException {
        synchronized (lock) {
            completeIndex.writeTo(tempBuf);
            writer.write(tempBuf.getInternalCharsArray(), 0, tempBuf.length());
            tempBuf.clear();

            super.saveTo(writer, tempBuf, pathToSave);
        }
    }

    @Override
    @SuppressWarnings("rawtypes")
    protected void restoreSavedInfo(Object o) throws MisconfigurationException {
        synchronized (lock) {
            Tuple readFromFile = (Tuple) o;
            if (!(readFromFile.o1 instanceof Tuple3)) {
                throw new RuntimeException("Type Error: the info must be regenerated (changed across versions).");
            }

            completeIndex = (DiskCache) readFromFile.o2;
            if (completeIndex == null) {
                throw new RuntimeException(
                        "Type Error (index == null): the info must be regenerated (changed across versions).");
            }

            String shouldBeOn = FileUtils.getFileAbsolutePath(getCompleteIndexPersistingFolder());
            if (!completeIndex.getFolderToPersist().equals(shouldBeOn)) {
                //this can happen if the user moves its .metadata folder (so, we have to validate it).
                completeIndex.setFolderToPersist(shouldBeOn);
            }

            super.restoreSavedInfo(readFromFile.o1);
        }
    }

    /**
     * actually does the load
     * @return true if it was successfully loaded and false otherwise
     */
    protected boolean load() {

        Throwable errorFound = null;
        synchronized (lock) {
            File file;
            try {
                file = getPersistingLocation();
            } catch (MisconfigurationException e) {
                Log.log("Unable to restore previous info... (persisting location not available).", e);
                return false;
            }
            if (file.exists() && file.isFile()) {
                try {
                    return loadContentsFromFile(file) != null;
                } catch (Throwable e) {
                    errorFound = new RuntimeException("Unable to read: " + file, e);
                }
            }
        }
        try {
            String msg = "Info: Rebuilding internal caches: " + this.getPersistingLocation();
            if (errorFound == null) {
                msg += " (Expected error to be provided and got no error!)";
                Log.log(IStatus.ERROR, msg, errorFound);

            } else {
                Log.log(IStatus.INFO, msg, errorFound);
            }
        } catch (Exception e1) {
            Log.log("Rebuilding internal caches (error getting persisting location).");
        }
        return false;
    }

    private Object loadContentsFromFile(File file)
            throws FileNotFoundException, IOException, MisconfigurationException {
        FileInputStream fileInputStream = new FileInputStream(file);
        try {
            //            Timer timer = new Timer();
            String expected = "-- VERSION_" + AbstractAdditionalTokensInfo.version; //X is the version
            InputStreamReader reader = new InputStreamReader(fileInputStream);
            FastBufferedReader bufferedReader = new FastBufferedReader(reader);
            FastStringBuffer string = bufferedReader.readLine();
            ObjectsPoolMap objectsPoolMap = new ObjectsInternPool.ObjectsPoolMap();
            if (string != null && string.startsWith("-- VERSION_")) {
                Tuple<Tuple3<Object, Object, Object>, Object> tupWithResults = new Tuple<Tuple3<Object, Object, Object>, Object>(
                        new Tuple3<Object, Object, Object>(
                                null, null, null),
                        null);
                Tuple3<Object, Object, Object> superTupWithResults = tupWithResults.o1;
                //tupWithResults.o2 = DiskCache
                if (string.toString().equals(expected)) {
                    //OK, proceed with new I/O format!
                    try {
                        try {
                            FastStringBuffer line;
                            Map<Integer, String> dictionary = null;
                            FastStringBuffer tempBuf = new FastStringBuffer(1024);
                            while ((line = bufferedReader.readLine()) != null) {
                                if (line.startsWith("-- ")) {

                                    if (line.startsWith("-- START TREE 1")) {
                                        superTupWithResults.o1 = TreeIO.loadTreeFrom(bufferedReader, dictionary,
                                                tempBuf.clear(), objectsPoolMap);

                                    } else if (line.startsWith("-- START TREE 2")) {
                                        superTupWithResults.o2 = TreeIO.loadTreeFrom(bufferedReader, dictionary,
                                                tempBuf.clear(), objectsPoolMap);

                                    } else if (line.startsWith("-- START DICTIONARY")) {
                                        dictionary = TreeIO.loadDictFrom(bufferedReader, tempBuf.clear(),
                                                objectsPoolMap);

                                    } else if (line.startsWith("-- START DISKCACHE")) {
                                        if (!line.startsWith("-- START DISKCACHE_" + DiskCache.VERSION)) {
                                            throw new RuntimeException("Disk cache version changed");
                                        }
                                        tupWithResults.o2 = DiskCache.loadFrom(bufferedReader, objectsPoolMap);

                                    } else if (line.startsWith("-- VERSION_")) {
                                        if (!line.endsWith(String.valueOf(AbstractAdditionalTokensInfo.version))) {
                                            throw new RuntimeException("Expected the version to be: "
                                                    + AbstractAdditionalTokensInfo.version + " Found: " + line);
                                        }
                                    } else if (line.startsWith("-- END TREE")) {
                                        //just skip it in this situation.
                                    } else {
                                        throw new RuntimeException("Unexpected line: " + line);
                                    }
                                }
                            }
                        } finally {
                            bufferedReader.close();
                        }
                    } finally {
                        reader.close();
                    }

                    restoreSavedInfo(tupWithResults);
                    //                    timer.printDiff("Time taken");
                    return tupWithResults;
                } else {
                    throw new RuntimeException("Version does not match. Found: " + string + ". Expected: " + expected);
                }

            } else {
                //Try the old way of loading it (backward compatibility).
                fileInputStream.close();
                //                Timer timer2 = new Timer();
                Object tupWithResults = IOUtils.readFromFile(file);
                restoreSavedInfo(tupWithResults);
                //                timer2.printDiff("IOUtils time");
                save(); //Save in new format!
                return tupWithResults;
            }

        } finally {
            try {
                fileInputStream.close();
            } catch (Exception e) {
                //Ignore error closing.
            }
        }
    }

    protected void addInfoToModuleOnRestoreInsertCommand(Tuple<ModulesKey, List<IInfo>> data) {
        CompleteIndexKey key = new CompleteIndexKey(data.o1);
        if (data.o1.file != null) {
            key.lastModified = data.o1.file.lastModified();
        }

        completeIndex.add(key);

        //current way (saves a list of iinfo)
        for (Iterator<IInfo> it = data.o2.iterator(); it.hasNext();) {
            IInfo info = it.next();
            if (info.getPath() == null || info.getPath().length() == 0) {
                this.add(info, TOP_LEVEL);

            } else {
                this.add(info, INNER);
            }
        }
    }

}
