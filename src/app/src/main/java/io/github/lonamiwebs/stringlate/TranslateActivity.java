package io.github.lonamiwebs.stringlate;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;

import io.github.lonamiwebs.stringlate.Interfaces.ProgressUpdateCallback;
import io.github.lonamiwebs.stringlate.ResourcesStrings.Resources;
import io.github.lonamiwebs.stringlate.ResourcesStrings.ResourcesString;
import io.github.lonamiwebs.stringlate.Utilities.RepoHandler;

public class TranslateActivity extends AppCompatActivity {

    //region Members

    private EditText mOriginalStringEditText;
    private EditText mTranslatedStringEditText;

    private Spinner mLocaleSpinner;
    private Spinner mStringIdSpinner;

    private String mSelectedLocale;
    private boolean mShowTranslated;

    private Resources mDefaultResources;
    private Resources mSelectedLocaleResources;

    private RepoHandler mRepo;

    //endregion

    //region Initialization

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_translate);

        mOriginalStringEditText = (EditText)findViewById(R.id.originalStringEditText);
        mTranslatedStringEditText = (EditText)findViewById(R.id.translatedStringEditText);

        mLocaleSpinner = (Spinner)findViewById(R.id.localeSpinner);
        mStringIdSpinner = (Spinner)findViewById(R.id.stringIdSpinner);

        mLocaleSpinner.setOnItemSelectedListener(eOnLocaleSelected);
        mStringIdSpinner.setOnItemSelectedListener(eOnStringIdSelected);

        // Retrieve the owner and repository name
        Intent intent = getIntent();
        String owner = intent.getStringExtra(MainActivity.EXTRA_REPO_OWNER);
        String repoName = intent.getStringExtra(MainActivity.EXTRA_REPO_NAME);

        mRepo = new RepoHandler(this, owner, repoName);
        setTitle(mRepo.toString());

        if (mRepo.hasLocale(null)) {
            mDefaultResources = mRepo.loadResources(null);
            loadLocalesSpinner();
            checkTranslationVisibility();
        } else {
            // This should never happen since it's checked when creating the repository
            Toast.makeText(this, R.string.no_strings_found_update,
                    Toast.LENGTH_LONG).show();
        }
    }

    //endregion

    //region Menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.translate_menu, menu);

        mShowTranslated = menu.findItem(R.id.showTranslatedCheckBox).isChecked();
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.updateStrings:
                updateStrings();
                return true;

            case R.id.addLocale:
                promptAddLocale();
                return true;

            case R.id.deleteString:
                deleteString();
                return true;
            case R.id.deleteLocale:
                promptDeleteLocale();
                return true;
            case R.id.deleteRepo:
                promptDeleteRepo();
                return true;

            case R.id.showTranslatedCheckBox:
                toggleShowTranslated(item);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //endregion

    //region UI events

    //region Menu events

    // Synchronize our local strings.xml files with the remote GitHub repository
    private void updateStrings() {
        final ProgressDialog progress = ProgressDialog.show(this,
                getString(R.string.loading_ellipsis), null, true);

        mRepo.syncResources(new ProgressUpdateCallback() {
            @Override
            public void onProgressUpdate(String title, String description) {
                progress.setTitle(title);
                progress.setMessage(description);
            }

            @Override
            public void onProgressFinished(String description, boolean status) {
                progress.dismiss();
                if (description != null)
                    Toast.makeText(getApplicationContext(), description, Toast.LENGTH_SHORT).show();

                mDefaultResources = mRepo.loadResources(null);
                loadLocalesSpinner();
            }
        });
    }

    // Prompts the user to add a new locale. If it exists,
    // no new file is created but the entered locale is selected.
    private void promptAddLocale() {
        final EditText et = new EditText(this);
        new AlertDialog.Builder(this)
                .setTitle(R.string.enter_locale)
                .setMessage(getString(R.string.enter_locale_long, Locale.getDefault().getLanguage()))
                .setView(et)
                .setPositiveButton(R.string.create, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        String locale = et.getText().toString().trim();
                        if (isValidLocale(locale)) {
                            if (mRepo.createLocale(locale)) {
                                loadLocalesSpinner();
                                setCurrentLocale(locale);
                            } else {
                                Toast.makeText(getApplicationContext(),
                                        R.string.create_locale_error,
                                        Toast.LENGTH_SHORT).show();
                            }
                        } else {
                            // The input locale is not a valid locale
                            Toast.makeText(getApplicationContext(),
                                    R.string.invalid_locale,
                                    Toast.LENGTH_SHORT).show();
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // Deletes the currently selected string ID, this needs no warning
    private void deleteString() {
        if (!ensureLocaleSelected())
            return;

        mSelectedLocaleResources.deleteId((String)mStringIdSpinner.getSelectedItem());
        mTranslatedStringEditText.setText("");
    }

    // Prompts the user whether they want to delete the selected locale or not
    // This does need warning since deleting a whole locale is a big deal
    private void promptDeleteLocale() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sure_question)
                .setMessage(getString(R.string.delete_locale_confirm_long, mSelectedLocale))
                .setPositiveButton(getString(R.string.delete_locale), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mRepo.deleteLocale(mSelectedLocale);
                        loadLocalesSpinner();
                        checkTranslationVisibility();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    // Prompts the user whether they want to delete the current "repository" clone or not
    // There is no need for me to tell whoever reading this that this does need confirmation
    private void promptDeleteRepo() {
        new AlertDialog.Builder(this)
                .setTitle(R.string.sure_question)
                .setMessage(getString(R.string.delete_repository_confirm_long, mRepo.toString()))
                .setPositiveButton(getString(R.string.delete_repository), new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialogInterface, int i) {
                        mRepo.delete();
                        finish();
                    }
                })
                .setNegativeButton(getString(R.string.cancel), null)
                .show();
    }

    // Toggles the "Show translated strings" checkbox and updates the spinner
    private void toggleShowTranslated(MenuItem item) {
        mShowTranslated = !mShowTranslated;
        item.setChecked(mShowTranslated);
        loadStringIDsSpinner();
    }

    //endregion

    //region Button events

    public void onPreviousClick(final View v) {
        incrementStringIdIndex(-1);
    }

    public void onNextClick(final View v) {
        incrementStringIdIndex(+1);
    }

    public void onSaveClick(final View v) {
        // TODO hmm when changing locale it will ask Save changes?
        if (mRepo.saveResources(mSelectedLocaleResources, mSelectedLocale))
            Toast.makeText(this, R.string.save_success, Toast.LENGTH_SHORT).show();
        else
            Toast.makeText(this, R.string.save_error, Toast.LENGTH_SHORT).show();
    }

    //endregion

    //region Spinner events

    AdapterView.OnItemSelectedListener
            eOnLocaleSelected = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int i, long l) {
            setCurrentLocale((String)parent.getItemAtPosition(i));
        }
        @Override
        public void onNothingSelected(AdapterView<?> parent) { }
    };

    AdapterView.OnItemSelectedListener
            eOnStringIdSelected = new AdapterView.OnItemSelectedListener() {
        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int i, long l) {
            String id = (String)parent.getItemAtPosition(i);
            mOriginalStringEditText.setText(mDefaultResources.getContent(id));
            mTranslatedStringEditText.setText(mSelectedLocaleResources.getContent(id));
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) { }
    };

    //endregion

    //endregion

    //region Spinner loading

    private void loadLocalesSpinner() {
        ArrayList<String> spinnerArray = new ArrayList<>();
        for (String locale : mRepo.getLocales())
            if (!locale.equals(RepoHandler.DEFAULT_LOCALE))
                spinnerArray.add(locale);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, spinnerArray);

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mLocaleSpinner.setAdapter(adapter);
    }

    private void loadStringIDsSpinner() {
        if (!ensureLocaleSelected())
            return;

        ArrayList<String> spinnerArray = new ArrayList<>();
        if (mShowTranslated) {
            for (ResourcesString rs : mDefaultResources)
                if (rs.isTranslatable())
                    spinnerArray.add(rs.getId());
        } else {
            // If we're not showing the strings with a translation, we also need to
            // make sure that the currently selected locale doesn't already have them
            for (ResourcesString rs : mDefaultResources)
                if (!mSelectedLocaleResources.contains(rs.getId()) && rs.isTranslatable())
                    spinnerArray.add(rs.getId());
        }

        ArrayAdapter<String> idAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, spinnerArray);

        idAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        ((Spinner) findViewById(R.id.stringIdSpinner)).setAdapter(idAdapter);
    }

    //endregion

    //region String and locale handling

    // Sets the current locale also updating the spinner selection
    private void setCurrentLocale(String locale) {
        mLocaleSpinner.setSelection(getItemIndex(mLocaleSpinner, locale));

        mSelectedLocale = locale;
        mSelectedLocaleResources = mRepo.loadResources(mSelectedLocale);
        checkTranslationVisibility();
        loadStringIDsSpinner();
    }

    //endregion

    //region Utilities

    // Checks whether the translation layout (EditText and previous/next buttons)
    // should be visible (there is at least one non-default locale) or not.
    void checkTranslationVisibility() {
        if (mLocaleSpinner.getCount() == 0) {
            Toast.makeText(this, R.string.add_locale_to_start, Toast.LENGTH_SHORT).show();
            findViewById(R.id.translationLayout).setVisibility(View.GONE);
        } else {
            findViewById(R.id.translationLayout).setVisibility(View.VISIBLE);
        }
    }

    // Ensures that there is at least a locale selected, otherwise shows a warning
    boolean ensureLocaleSelected() {
        if (mSelectedLocaleResources == null) {
            Toast.makeText(this, R.string.no_locale_selected, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    boolean isValidLocale(String locale) {
        if (locale.contains("-")) {
            // If there is an hyphen, then a country was also specified
            for (Locale l : Locale.getAvailableLocales())
                if (!l.getCountry().isEmpty())
                    if (locale.equals(l.getLanguage()+"-"+l.getCountry()))
                        return true;
        } else {
            for (Locale l : Locale.getAvailableLocales())
                if (locale.equals(l.getLanguage()))
                    return true;
        }
        return false;
    }

    // Increments the mStringIdSpinner index by delta i (di),
    // clamping the value if it's less than 0 or value ≥ IDs count.
    private void incrementStringIdIndex(int di) {
        if (!ensureLocaleSelected())
            return;

        int i = mStringIdSpinner.getSelectedItemPosition() + di;
        if (i > -1) {
            if (i < mStringIdSpinner.getCount()) {
                String resourceId = (String)mStringIdSpinner.getSelectedItem();
                String content = mTranslatedStringEditText.getText().toString();
                mSelectedLocaleResources.setContent(resourceId, content);

                mStringIdSpinner.setSelection(i);
            } else {
                Toast.makeText(this, R.string.no_strings_left, Toast.LENGTH_SHORT).show();
            }
        }
    }

    // Sadly, the spinners don't provide any method to retrieve
    // an item position given its value. This method helps that
    private int getItemIndex(Spinner spinner, String str) {
        for (int i = 0; i < spinner.getCount(); i++)
            if (spinner.getItemAtPosition(i).toString().equalsIgnoreCase(str))
                return i;
        return -1;
    }

    //endregion
}