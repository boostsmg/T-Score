package co.tinode.tindroid;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.lifecycle.ViewModelProvider;

/**
 * Starting a new chat or selecting a trusted person for a deal.
 */
public class StartChatActivity extends BaseActivity
        implements FindFragment.ReadContactsPermissionChecker,
        ImageViewFragment.AvatarCompletionHandler {

    public static final String EXTRA_SELECT_FOR_DEAL = "tscore.select_for_deal";
    public static final String EXTRA_SELECTED_USER_ID = "tscore.selected_user_id";

    static final String FRAGMENT_TABS = "tabs";
    static final String FRAGMENT_AVATAR_PREVIEW = "avatar_preview";

    private boolean mReadContactsPermissionsAlreadyRequested = false;
    private AvatarViewModel mAvatarVM;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UiUtils.setupSystemToolbar(this);
        setContentView(R.layout.activity_create);
        applyEdgeToEdgeInsets(findViewById(android.R.id.content));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setTitle(isDealSelectionMode() ? "Select person for deal" : getString(R.string.action_new_chat));
            toolbar.setNavigationOnClickListener(v -> {
                if (isDealSelectionMode()) {
                    setResult(Activity.RESULT_CANCELED);
                    finish();
                    return;
                }
                Intent intent = new Intent(StartChatActivity.this, ChatsActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
                startActivity(intent);
                finish();
            });
        }

        showFragment(FRAGMENT_TABS, null, false);
        mAvatarVM = new ViewModelProvider(this).get(AvatarViewModel.class);
    }

    public boolean isDealSelectionMode() {
        Intent intent = getIntent();
        return intent != null && intent.getBooleanExtra(EXTRA_SELECT_FOR_DEAL, false);
    }

    public void completeDealSelection(String userId) {
        if (!isDealSelectionMode() || userId == null || userId.isBlank() || "slf".equals(userId)) {
            return;
        }
        Intent result = new Intent();
        result.putExtra(EXTRA_SELECTED_USER_ID, userId);
        setResult(Activity.RESULT_OK, result);
        finish();
    }

    public boolean shouldRequestReadContactsPermission() {
        return !mReadContactsPermissionsAlreadyRequested;
    }

    public void setReadContactsPermissionRequested() {
        mReadContactsPermissionsAlreadyRequested = true;
    }

    @Override
    public void onAcceptAvatar(String topicName, Bitmap avatar) {
        if (isDestroyed() || isFinishing()) {
            return;
        }
        mAvatarVM.setAvatar(avatar);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
    }

    void showFragment(String tag, Bundle args, Boolean addToBackstack) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(tag);
        if (fragment == null) {
            switch (tag) {
                case FRAGMENT_TABS:
                    fragment = new StartChatFragment();
                    break;
                case FRAGMENT_AVATAR_PREVIEW:
                    fragment = new ImageViewFragment();
                    if (args == null) {
                        args = new Bundle();
                    }
                    args.putBoolean(AttachmentHandler.ARG_AVATAR, true);
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }

        if (fragment.getArguments() != null) {
            fragment.getArguments().putAll(args);
        } else {
            fragment.setArguments(args);
        }

        FragmentTransaction tx = fm.beginTransaction()
                .replace(R.id.contentFragment, fragment)
                .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
        if (addToBackstack) {
            tx = tx.addToBackStack(null);
        }
        tx.commitAllowingStateLoss();
    }
}