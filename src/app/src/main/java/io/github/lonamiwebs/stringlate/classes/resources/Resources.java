package io.github.lonamiwebs.stringlate.classes.resources;

import android.support.annotation.NonNull;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;

import io.github.lonamiwebs.stringlate.classes.resources.tags.ResPlurals;
import io.github.lonamiwebs.stringlate.classes.resources.tags.ResStringArray;
import io.github.lonamiwebs.stringlate.classes.resources.tags.ResTag;

// Class to manage multiple ResTag,
// usually parsed from strings.xml files
public class Resources implements Iterable<ResTag> {

    //region Members

    private final File mFile; // Keep track of the original file to be able to save()
    private final HashSet<ResTag> mStrings;
    private final HashSet<String> mUnsavedIDs;

    private ResTag mLastTag; // The last tag returned by getTag()

    private boolean mSavedChanges;
    private boolean mModified;

    //endregion

    //region Constructors

    @NonNull
    public static Resources fromFile(File file) {
        if (file.isFile()) {
            InputStream is = null;
            try {
                is = new FileInputStream(file);
                HashSet<ResTag> result = ResourcesParser.parseFromXml(is);
                return new Resources(file, result);
            } catch (IOException | XmlPullParserException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (is != null)
                        is.close();
                } catch (IOException ignored) { }
            }
        }
        return new Resources(file, new HashSet<ResTag>());
    }

    // Empty resources cannot be saved
    @NonNull
    public static Resources empty() {
        return new Resources(null, new HashSet<ResTag>());
    }

    private Resources(File file, HashSet<ResTag> strings) {
        mFile = file;
        mStrings = strings;
        mSavedChanges = mFile != null && mFile.isFile();

        // Keep track of the unsaved strings not to iterate over the list to count them
        mUnsavedIDs = new HashSet<>();

        // Backwards compatibility with version 0.9
        // If the .modified file exists, assume all the local strings were modified
        // This will allow old users to pull new changes without overwriting local
        // TODO Remove this on version 1.0 (or similar)

        // -- Begin of backwards-compatibility code
        String path = mFile == null ? "" : mFile.getAbsolutePath();
        int extensionIndex = path.lastIndexOf('.');
        if (extensionIndex > -1)
            path = path.substring(0, extensionIndex);
        path += ".modified";
        File mModifiedFile = new File(path);

        if (mModifiedFile.isFile()) {
            for (ResTag rs : mStrings) {
                String content = rs.getContent();
                // Clear the content and then set the original one
                // so 'modified' equals true
                rs.setContent("");
                rs.setContent(content);
            }
            // Set saved changes = false to force saving
            mSavedChanges = false;
            save();

            // Delete the file, it's now useless
            mModifiedFile.delete();
        }
        // -- End of backwards-compatibility code

        mModified = false;
        for (ResTag rs : mStrings) {
            if (rs.wasModified()) {
                mModified = true;
                break;
            }
        }
    }

    //endregion

    //region Getting content

    public int count() {
        return mStrings.size();
    }

    public int unsavedCount() {
        return mUnsavedIDs.size();
    }

    public boolean isEmpty() {
        return mStrings.isEmpty();
    }

    public boolean contains(String resourceId) {
        return getTag(resourceId) != null;
    }

    public String getContent(String resourceId) {
        ResTag tag = getTag(resourceId);
        return tag == null ? "" : tag.getContent();
    }

    public ResTag getTag(String resourceId) {
        if (mLastTag != null && mLastTag.getId().equals(resourceId)) {
            return mLastTag;
        }

        mLastTag = null;
        // TODO Make a constant value for ':' somewhere else
        if (resourceId.contains(":")) {
            for (ResTag rs : mStrings) {
                if (rs.getId().equals(resourceId)) {
                    mLastTag = rs;
                    break;
                }
            }
        } else {
            for (ResTag rs : mStrings) {
                if (rs instanceof ResStringArray.Item) {
                    if (((ResStringArray.Item)rs).getParent().getId().equals(resourceId))
                        return mLastTag = rs;
                } else if (rs instanceof ResPlurals.Item) {
                    if (((ResPlurals.Item)rs).getParent().getId().equals(resourceId))
                        return mLastTag = rs;
                }
                if (rs.getId().equals(resourceId))
                    return mLastTag = rs;
            }
        }

        return mLastTag;
    }

    // Determines whether the resource ID was modified or not
    // If this resource ID doesn't exist, then it obviously wasn't modified
    public boolean wasModified(String resourceId) {
        ResTag rs = getTag(resourceId);
        return rs != null && rs.wasModified();
    }

    //endregion

    //region Updating (setting) content

    public void setContent(ResTag original, @NonNull String content) {
        String resourceId = original == null ? "" : original.getId();
        if (resourceId.isEmpty())
            return;

        // If the content is empty (or null), treat it as deleting this ID
        if (content.isEmpty()) {
            deleteId(resourceId);
            return;
        }

        ResTag rs = getTag(resourceId);
        if (rs != null) {
            if (rs.setContent(content)) {
                mSavedChanges = false;
                mUnsavedIDs.add(resourceId);
            }
        } else {
            // We need to treat string arrays and plurals specially
            // For these, we need to find the parent, and if it exists
            // then we need to add the child to the existing parent
            boolean handled = false;
            if (original instanceof ResStringArray.Item) {
                ResStringArray.Item ori = (ResStringArray.Item)original;
                ResStringArray.Item existingChild =
                        (ResStringArray.Item)getTag(ori.getParent().getId());

                if (existingChild != null) {
                    // The parent existed, so add the new string to it, and the
                    // resulting new string to our local array of children
                    ResStringArray parent = existingChild.getParent();
                    mStrings.add(parent.addItem(content, true, ori.getIndex()));
                    handled = true;
                } // else the parent didn't exist, so behave as the general case

            } else if (original instanceof ResPlurals.Item) {
                ResPlurals.Item ori = (ResPlurals.Item)original;
                ResPlurals.Item existingChild =
                        (ResPlurals.Item)getTag(ori.getParent().getId());

                if (existingChild != null) {
                    // The parent existed, so add the new string to it, and the
                    // resulting new string to our local array of children
                    ResPlurals parent = existingChild.getParent();
                    mStrings.add(parent.addItem(ori.getQuantity(), content, true));
                    handled = true;
                } // else the parent didn't exist, so behave as the general case
            }
            if (!handled) {
                ResTag clone = original.clone(content);
                mStrings.add(clone);
            }
            mSavedChanges = false;
            mUnsavedIDs.add(resourceId);
        }
    }

    public void addTag(ResTag rt) {
        if (mStrings.add(rt))
            mSavedChanges = false;
    }

    //endregion

    //region Deleting content

    public void deleteId(String resourceId) {
        for (ResTag rs : mStrings)
            if (rs.getId().equals(resourceId)) {
                mStrings.remove(rs);
                break;
            }
    }

    //endregion

    //region File saving and deleting

    public String getFilename() {
        return mFile == null ? "strings.xml" : mFile.getName();
    }

    // Determines whether the files was saved or not
    public boolean areSaved() {
        return mSavedChanges;
    }

    // Determines whether the file was ever modified or not (any of its strings were modified)
    public boolean wasModified() { return mModified; }

    // If there are unsaved changes, saves the file
    // If the file was saved successfully or there were no changes to save, returns true
    public boolean save() {
        if (mSavedChanges)
            return true;

        if (mFile == null)
            return false;

        try {
            if (!mFile.getParentFile().isDirectory())
                mFile.getParentFile().mkdirs();

            FileOutputStream out = new FileOutputStream(mFile);
            mSavedChanges = ResourcesParser.parseToXml(this, out);
            mModified = true;
            out.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        // We do not want empty files, if it exists and it's empty delete it
        if (mFile.isFile() && mFile.length() == 0)
            mFile.delete();

        // Clear the unsaved IDs if we succeeded
        if (mSavedChanges)
            mUnsavedIDs.clear();

        return mFile.isFile();
    }

    public boolean delete() {
        boolean ok = mFile != null && mFile.delete();
        if (ok) {
            // If the directory is empty, delete it too
            File parent = mFile.getParentFile();
            String[] children = parent.list();
            if (children == null || children.length == 0) {
                ok = parent.delete();
            }
        }
        return ok;
    }

    //endregion

    //region Iterator wrapper

    @Override
    public Iterator<ResTag> iterator() {
        ArrayList<ResTag> strings = new ArrayList<>(mStrings);
        Collections.sort(strings);
        return strings.iterator();
    }

    //endregion
}
