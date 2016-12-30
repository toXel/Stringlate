package io.github.lonamiwebs.stringlate.activities.export;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.util.Pair;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;

import io.github.lonamiwebs.stringlate.R;
import io.github.lonamiwebs.stringlate.classes.LocaleString;
import io.github.lonamiwebs.stringlate.settings.AppSettings;
import io.github.lonamiwebs.stringlate.utilities.GitHub;
import io.github.lonamiwebs.stringlate.utilities.RepoHandler;

import static io.github.lonamiwebs.stringlate.utilities.Constants.EXTRA_LOCALE;
import static io.github.lonamiwebs.stringlate.utilities.Constants.EXTRA_REMOTE_PATH;
import static io.github.lonamiwebs.stringlate.utilities.Constants.EXTRA_REPO;
import static io.github.lonamiwebs.stringlate.utilities.Constants.EXTRA_XML_CONTENT;

public class CreatePullRequestActivity extends AppCompatActivity {

    //region Members

    private AppSettings mSettings;

    private TextView mInfoTextView;
    private Spinner mBranchesSpinner;
    private EditText mCommitMessageEditText;

    private RepoHandler mRepo;
    private String mXmlContent;
    private String mLocale;
    private String mRemotePath;
    private String mUsername;

    private Boolean mNeedFork = null;

    //endregion

    //region Initialization

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_pull_request);

        mInfoTextView = (TextView)findViewById(R.id.infoTextView);
        mBranchesSpinner = (Spinner)findViewById(R.id.branchesSpinner);
        mCommitMessageEditText = (EditText)findViewById(R.id.commitMessageEditText);

        mSettings = new AppSettings(this);

        Intent intent = getIntent();
        mRepo = RepoHandler.fromBundle(this, intent.getBundleExtra(EXTRA_REPO));
        mXmlContent = intent.getStringExtra(EXTRA_XML_CONTENT);
        mLocale = intent.getStringExtra(EXTRA_LOCALE);
        mRemotePath = intent.getStringExtra(EXTRA_REMOTE_PATH);

        mCommitMessageEditText.setText(getString(
                R.string.added_x_translation_spam, mLocale, LocaleString.getDisplay(mLocale)));

        checkPermissions();
        checkBranches();
    }

    //endregion

    //region First time setup

    void checkPermissions() {
        new AsyncTask<Void, Void, Pair<String, Boolean>>() {
            @Override
            protected Pair<String, Boolean> doInBackground(Void... params) {
                return GitHub.gCanPush(mSettings.getGitHubToken(), mRepo);
            }

            @Override
            protected void onPostExecute(Pair<String, Boolean> canPush) {
                if (canPush.second) {
                    mInfoTextView.setText(R.string.can_push_no_pr);
                } else {
                    mInfoTextView.setText(R.string.cannot_push_will_pr);
                }
                mUsername = canPush.first;
                //mNeedFork = !canPush.second;
                // TODO Hahaha hackz xd
                mNeedFork = true;
            }
        }.execute();
    }

    void checkBranches() {
        new AsyncTask<Void, Void, ArrayList<String>>() {
            @Override
            protected ArrayList<String> doInBackground(Void... params) {
                ArrayList<String> result = new ArrayList<>();

                JSONArray branches = GitHub.gGetBranches(mRepo);
                if (branches != null) {
                    try {
                        for (int i = 0; i < branches.length(); i++) {
                            result.add(branches.getJSONObject(i).getString("name"));
                        }
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

                return result;
            }

            @Override
            protected void onPostExecute(ArrayList<String> branches) {
                loadBranchesSpinner(branches);
            }
        }.execute();
    }

    //endregion

    //region Spinner loading

    private void loadBranchesSpinner(ArrayList<String> branches) {
        ArrayAdapter<String> idAdapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item, branches);

        idAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mBranchesSpinner.setAdapter(idAdapter);
    }

    //endregion

    //region Button events

    public void commitChanges(View view) {
        final String branch = (String)mBranchesSpinner.getSelectedItem();
        if (mNeedFork == null || branch == null) {
            Toast.makeText(this, R.string.loading_ellipsis, Toast.LENGTH_SHORT).show();
            return;
        }

        final String commitMessage = mCommitMessageEditText.getText().toString().trim();
        if (commitMessage.isEmpty()) {
            Toast.makeText(this, R.string.commit_msg_empty, Toast.LENGTH_SHORT).show();
            return;
        }

        // There must be a non-empty title if we want it to be set later
        final ProgressDialog progress = ProgressDialog.show(this, "…", "…", true);

        final String token = mSettings.getGitHubToken();
        final Context ctx = this;
        new AsyncTask<Void, PUData, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                RepoHandler repo;
                if (mNeedFork) {
                    publishProgress(new PUData(getString(R.string.forking_repo),
                            getString(R.string.forking_repo_long)));

                    // Fork the repository
                    try {
                        JSONObject fork = GitHub.gForkRepository(token, mRepo);
                        if (fork == null) throw new JSONException("Resulting fork is null.");

                        String owner = fork.getJSONObject("owner").getString("login");
                        String repoName = fork.getString("name");
                        repo = new RepoHandler(ctx, owner, repoName);
                    } catch (JSONException e) {
                        e.printStackTrace();
                        publishProgress(new PUData(getString(R.string.fork_failed)));
                        return false;
                    }
                } else {
                    repo = mRepo;
                }

                // Commit the file
                try {
                    publishProgress(new PUData(getString(R.string.creating_commit),
                            getString(R.string.creating_commit_long)));

                    GitHub.gCreateCommitFile(token, repo, branch,
                            mXmlContent, mRemotePath, commitMessage);
                } catch (JSONException e) {
                    publishProgress(new PUData(getString(R.string.commit_failed)));
                    e.printStackTrace();
                    return false;
                }

                if (mNeedFork) {
                    // Create pull request
                    publishProgress(new PUData(getString(R.string.creating_pr),
                            getString(R.string.creating_pr_long)));

                    String title, body;
                    int newLineIndex = commitMessage.indexOf('\n');
                    if (newLineIndex > -1) {
                        title = commitMessage.substring(0, newLineIndex);
                        body = commitMessage.substring(newLineIndex);
                    } else {
                        title = commitMessage;
                        body = "";
                    }
                    return GitHub.gCreatePullRequest(token, mRepo, title,
                            mUsername+":"+branch, branch, body) != null;
                }
                return true;
            }

            @Override
            protected void onProgressUpdate(PUData... values) {
                PUData data = values[0];
                if (data.done) {
                    progress.dismiss();
                    Toast.makeText(ctx, data.title, Toast.LENGTH_SHORT).show();
                } else {
                    progress.setTitle(data.title);
                    progress.setTitle(data.message);
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                if (success) {
                    Toast.makeText(ctx, R.string.done, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(ctx, R.string.something_went_wrong, Toast.LENGTH_SHORT).show();
                }
                progress.dismiss();
            }
        }.execute();
    }

    //endregion

    //region Sub classes

    // TODO Find a better way to handle async tasks and ProgressUpdateCallback
    class PUData {
        public final String title;
        public final String message;
        public final boolean done;

        public PUData(String title, String message) {
            this.title = title;
            this.message = message;
            this.done = false;
        }

        public PUData(String doneTitle) {
            this.title = doneTitle;
            this.message = null;
            this.done = true;
        }
    }

    //endregion
}