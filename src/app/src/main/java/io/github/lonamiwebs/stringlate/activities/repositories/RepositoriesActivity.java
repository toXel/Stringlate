package io.github.lonamiwebs.stringlate.activities.repositories;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.TabLayout;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import io.github.lonamiwebs.stringlate.R;
import io.github.lonamiwebs.stringlate.activities.GitHubLoginActivity;
import io.github.lonamiwebs.stringlate.activities.OnlineHelpActivity;
import io.github.lonamiwebs.stringlate.activities.translate.TranslateActivity;
import io.github.lonamiwebs.stringlate.classes.repos.RepoHandler;
import io.github.lonamiwebs.stringlate.utilities.Api;

import static io.github.lonamiwebs.stringlate.utilities.Constants.RESULT_REPO_DISCOVERED;

public class RepositoriesActivity extends AppCompatActivity {

    //region Members

    private RepositoriesPagerAdapter mRepositoriesPagerAdapter;
    private ViewPager mViewPager;

    //endregion

    //region Initialization

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_repositories);

        // Compatibility code
        try {
            RepoHandler.checkUpgradeRepositories(this);
        } catch (Exception e) {
            // We don't want any upgrade checking to break our application…
            e.printStackTrace();
        }

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mRepositoriesPagerAdapter = new RepositoriesPagerAdapter(getSupportFragmentManager(), this);
        mViewPager = (ViewPager)findViewById(R.id.container);
        mViewPager.setAdapter(mRepositoriesPagerAdapter);

        TabLayout tabLayout = (TabLayout)findViewById(R.id.tabs);
        tabLayout.setupWithViewPager(mViewPager);

        // Check if we opened the application because a GitHub link was clicked
        // If this is the case then we should show the "Add repository" fragment
        final Intent intent = getIntent();
        final String action = intent.getAction();
        if (action.equals(Intent.ACTION_VIEW)) {
            // Opened via a GitHub.com url
            mViewPager.setCurrentItem(1, false);
        } else if (action.equals(Api.ACTION_TRANSLATE)) {

            // Opened via our custom Api, ensure we have the required extras
            if (intent.hasExtra(Api.EXTRA_GIT_URL)) {
                final String gitUrl = intent.getStringExtra(Api.EXTRA_GIT_URL);
                RepoHandler repo = new RepoHandler(this, gitUrl);
                if (repo.isEmpty()) {
                    // This repository is empty, clean any created
                    // garbage and show the "Add repository" fragment
                    repo.delete();
                    mViewPager.setCurrentItem(1, false);
                } else {
                    // We already had this repository so directly
                    // show the "Translate" activity and finish this
                    TranslateActivity.launch(this, repo);
                    finish();
                }
            } else {
                // No extra was given, finish taking no further action
                finish();
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();

        // Always notify data set changed to refresh the repository list
        mRepositoriesPagerAdapter.notifyDataSetChanged();
    }

    //endregion

    //region Menu

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.menu_repositories, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            // Online help
            case R.id.help:
                // Avoid the "Remove unused resources" from removing these files…
                if (R.raw.en != 0 && R.raw.es != 0) {
                    startActivity(new Intent(this, OnlineHelpActivity.class));
                }
                return true;
            // Login to GitHub
            case R.id.github_login:
                startActivity(new Intent(this, GitHubLoginActivity.class));
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    //endregion

    //region Activity results

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode == RESULT_OK) {
            switch (requestCode) {
                case RESULT_REPO_DISCOVERED:
                    // Position #1 is the "Add new repository" fragment
                    mViewPager.setCurrentItem(1, true);
                    // Let the child fragment know this activity result occurred
                default:
                    super.onActivityResult(requestCode, resultCode, data);
                    break;
            }
        }
    }

    //endregion
}
