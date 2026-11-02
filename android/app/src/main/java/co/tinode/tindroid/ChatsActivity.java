package co.tinode.tindroid;

import android.Manifest;
import android.accounts.Account;
import android.accounts.AccountManager;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.List;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import co.tinode.tindroid.account.ContactsManager;
import co.tinode.tindroid.account.Utils;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.MeTopic;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.Topic;
import co.tinode.tinodesdk.model.Credential;
import co.tinode.tinodesdk.model.Description;
import co.tinode.tinodesdk.model.MsgServerInfo;
import co.tinode.tinodesdk.model.MsgServerPres;
import co.tinode.tinodesdk.model.PrivateType;
import co.tinode.tinodesdk.model.Subscription;

/**
 * This activity owns 'me' topic.
 */
public class ChatsActivity extends BaseActivity
        implements UiUtils.ProgressIndicator, UtilsMedia.MediaPreviewer,
        ImageViewFragment.AvatarCompletionHandler {
    private static final String TAG = "ChatsActivity";

    static final String TAG_FRAGMENT_NAME = "fragment";
    static final String FRAGMENT_CHATLIST = "contacts";
    static final String FRAGMENT_DEALS = "deals";
    static final String FRAGMENT_PEOPLE = "people";
    static final String FRAGMENT_TRUST_PROFILE = "trust_profile";
    static final String FRAGMENT_ACCOUNT_INFO = "account_info";
    static final String FRAGMENT_AVATAR_PREVIEW = "avatar_preview";
    static final String FRAGMENT_ACC_CREDENTIALS = "acc_credentials";
    static final String FRAGMENT_ACC_HELP = "acc_help";
    static final String FRAGMENT_ACC_GENERAL = "acc_general";
    static final String FRAGMENT_ACC_NOTIFICATIONS = "acc_notifications";
    static final String FRAGMENT_ACC_PERSONAL = "acc_personal";
    static final String FRAGMENT_ACC_SECURITY = "acc_security";
    static final String FRAGMENT_ACC_ABOUT = "acc_about";
    static final String FRAGMENT_ARCHIVE = "archive";
    static final String FRAGMENT_BANNED = "banned";
    static final String FRAGMENT_WALLPAPERS = "wallpapers";

    private ContactsEventListener mTinodeListener = null;
    private MeListener mMeTopicListener = null;
    private MeTopic<VxCard> mMeTopic = null;

    private Account mAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        UiUtils.setupSystemToolbar(this);

        setContentView(R.layout.activity_contacts);
        applyEdgeToEdgeInsets(findViewById(android.R.id.content));

        setSupportActionBar(findViewById(R.id.toolbar));

        BottomNavigationView bottomNav = findViewById(R.id.mainBottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();
            if (id == R.id.tabChats) {
                showMainTab(FRAGMENT_CHATLIST);
                return true;
            } else if (id == R.id.tabDeals) {
                showMainTab(FRAGMENT_DEALS);
                return true;
            } else if (id == R.id.tabPeople) {
                showMainTab(FRAGMENT_PEOPLE);
                return true;
            } else if (id == R.id.tabProfile) {
                showMainTab(FRAGMENT_TRUST_PROFILE);
                return true;
            }
            return false;
        });

        FragmentManager fm = getSupportFragmentManager();
        if (fm.findFragmentById(R.id.contentFragment) == null) {
            showMainTab(FRAGMENT_CHATLIST);
        } else {
            Fragment current = fm.findFragmentById(R.id.contentFragment);
            String currentTag = current == null ? null : current.getTag();
            updateMainTabStyle(TextUtils.isEmpty(currentTag) ? FRAGMENT_CHATLIST : currentTag);
        }

        // Existing/older installs may restore a local DB/session that is no longer
        // usable after an upgrade. Never let that take down the whole process.
        try {
            mMeTopic = Cache.getTinode().getOrCreateMeTopic();
            mMeTopicListener = new MeListener();
        } catch (Throwable t) {
            Log.e(TAG, "Failed to initialize logged-in session; returning to login", t);
            recoverToLogin();
        }
    }

    private void showMainTab(String tag) {
        if (isFinishing() || isDestroyed()) {
            return;
        }

        Fragment current = getSupportFragmentManager().findFragmentById(R.id.contentFragment);
        if (current != null && tag.equals(current.getTag()) && current.isAdded()) {
            updateMainTabStyle(tag);
            return;
        }

        Fragment fragment;
        String title;
        switch (tag) {
            case FRAGMENT_DEALS:
                fragment = new DealsFragment();
                title = "Deals";
                break;
            case FRAGMENT_PEOPLE:
                fragment = new PeopleFragment();
                title = "People";
                break;
            case FRAGMENT_TRUST_PROFILE:
                fragment = new TrustProfileFragment();
                title = "My T-Score";
                break;
            case FRAGMENT_CHATLIST:
            default:
                fragment = new ChatsFragment();
                title = "T-Score";
                break;
        }

        try {
            FragmentTransaction trx = getSupportFragmentManager().beginTransaction()
                    .replace(R.id.contentFragment, fragment, tag);
            if (FRAGMENT_CHATLIST.equals(tag)) {
                trx.setPrimaryNavigationFragment(fragment);
            }
            trx.commitAllowingStateLoss();
            if (getSupportActionBar() != null) getSupportActionBar().setTitle(title);
            updateMainTabStyle(tag);
        } catch (RuntimeException e) {
            // A restored FragmentManager state from an older APK must not cause a
            // permanent close-loop. Log it and fall back to the stable Chats tab.
            Log.e(TAG, "Failed to show main tab " + tag, e);
            if (!FRAGMENT_CHATLIST.equals(tag)) {
                try {
                    Fragment fallback = new ChatsFragment();
                    getSupportFragmentManager().beginTransaction()
                            .replace(R.id.contentFragment, fallback, FRAGMENT_CHATLIST)
                            .commitAllowingStateLoss();
                    updateMainTabStyle(FRAGMENT_CHATLIST);
                } catch (RuntimeException fallbackError) {
                    Log.e(TAG, "Chats fallback also failed", fallbackError);
                    recoverToLogin();
                }
            } else {
                recoverToLogin();
            }
        }
    }

    private void updateMainTabStyle(String selected) {
        BottomNavigationView bottomNav = findViewById(R.id.mainBottomNav);
        if (bottomNav == null) return;
        int itemId;
        if (FRAGMENT_CHATLIST.equals(selected)) itemId = R.id.tabChats;
        else if (FRAGMENT_DEALS.equals(selected)) itemId = R.id.tabDeals;
        else if (FRAGMENT_PEOPLE.equals(selected)) itemId = R.id.tabPeople;
        else if (FRAGMENT_TRUST_PROFILE.equals(selected)) itemId = R.id.tabProfile;
        else return;

        // Do not re-select an already-selected item. BottomNavigationView invokes
        // OnItemSelectedListener from setSelectedItemId(); re-selecting here used
        // to recurse showMainTab() -> updateMainTabStyle() until StackOverflow.
        if (bottomNav.getSelectedItemId() != itemId) {
            bottomNav.setSelectedItemId(itemId);
        }
    }

    /**
     * onResume restores subscription to 'me' topic and sets listener.
     */
    @Override
    public void onResume() {
        super.onResume();

        if (mMeTopic == null || mMeTopicListener == null) {
            Log.e(TAG, "Missing MeTopic during resume; recovering to login");
            recoverToLogin();
            return;
        }

        try {
            final Tinode tinode = Cache.getTinode();
            if (tinode == null) {
                Log.e(TAG, "Missing Tinode cache during resume; recovering to login");
                recoverToLogin();
                return;
            }

            mTinodeListener = new ContactsEventListener(tinode.isConnected());
            tinode.addListener(mTinodeListener);

            Cache.setSelectedTopicName(null);

            UiUtils.setupToolbar(this, null, null, false,
                    null, false, 0);

            if (!mMeTopic.isAttached()) {
                toggleProgressIndicator(true);
            }

            // This will issue a subscription request.
            if (!UiUtils.attachMeTopic(this, mMeTopicListener)) {
                toggleProgressIndicator(false);
            }
        } catch (Throwable t) {
            Log.e(TAG, "Failed to restore logged-in session; recovering to login", t);
            recoverToLogin();
            return;
        }

        final Intent intent = getIntent();
        String tag = intent.getStringExtra(TAG_FRAGMENT_NAME);
        if (!TextUtils.isEmpty(tag)) {
            // Consume one-shot navigation. Otherwise onResume may try to open the
            // same stale destination repeatedly after background/foreground cycles.
            intent.removeExtra(TAG_FRAGMENT_NAME);
            showFragment(tag, null);
        }
    }

    private void datasetChanged() {
        Fragment fragment = UiUtils.getVisibleFragment(getSupportFragmentManager());
        if (fragment instanceof ChatsFragment) {
            ((ChatsFragment) fragment).datasetChanged();
        }
    }

    @Override
    public void onPause() {
        super.onPause();

        try {
            Tinode tinode = Cache.getTinode();
            if (tinode != null && mTinodeListener != null) {
                tinode.removeListener(mTinodeListener);
            }
        } catch (Throwable t) {
            Log.w(TAG, "Failed to remove Tinode listener during pause", t);
        } finally {
            mTinodeListener = null;
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mMeTopic != null && mMeTopicListener != null) {
            try {
                mMeTopic.remListener(mMeTopicListener);
            } catch (Throwable t) {
                Log.w(TAG, "Failed to remove MeTopic listener during stop", t);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Enable options menu by returning true
        return true;
    }

    @Override
    public void handleMedia(Bundle args) {
        showFragment(FRAGMENT_AVATAR_PREVIEW, args);
    }

    void showFragment(String tag, Bundle args) {
        if (isFinishing() || isDestroyed() || TextUtils.isEmpty(tag)) {
            return;
        }

        // Main-tab destinations are handled by the main navigation path. Older
        // intents/deep links may still send these tags through showFragment().
        if (FRAGMENT_CHATLIST.equals(tag) || FRAGMENT_DEALS.equals(tag)
                || FRAGMENT_PEOPLE.equals(tag) || FRAGMENT_TRUST_PROFILE.equals(tag)) {
            showMainTab(tag);
            return;
        }

        final FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentByTag(tag);
        if (fragment == null) {
            switch (tag) {
                case FRAGMENT_ACCOUNT_INFO:
                    fragment = new AccountInfoFragment();
                    break;
                case FRAGMENT_ACC_CREDENTIALS:
                    fragment = new AccCredFragment();
                    break;
                case FRAGMENT_ACC_HELP:
                    fragment = new AccHelpFragment();
                    break;
                case FRAGMENT_ACC_GENERAL:
                    fragment = new AccGeneralFragment();
                    break;
                case FRAGMENT_ACC_NOTIFICATIONS:
                    fragment = new AccNotificationsFragment();
                    break;
                case FRAGMENT_ACC_PERSONAL:
                    fragment = new AccPersonalFragment();
                    break;
                case FRAGMENT_AVATAR_PREVIEW:
                    fragment = new ImageViewFragment();
                    if (args == null) {
                        args = new Bundle();
                    }
                    args.putBoolean(AttachmentHandler.ARG_AVATAR, true);
                    break;
                case FRAGMENT_ACC_SECURITY:
                    fragment = new AccSecurityFragment();
                    break;
                case FRAGMENT_ACC_ABOUT:
                    fragment = new AccAboutFragment();
                    break;
                case FRAGMENT_ARCHIVE:
                case FRAGMENT_BANNED:
                    fragment = new ChatsFragment();
                    if (args == null) {
                        args = new Bundle();
                    }
                    args.putBoolean(tag, true);
                    break;
                case FRAGMENT_WALLPAPERS:
                    fragment = new WallpaperFragment();
                    break;
                default:
                    // Do not crash the whole app because of a stale/unknown intent
                    // left by an older build or notification.
                    Log.w(TAG, "Ignoring unknown fragment tag: " + tag);
                    showMainTab(FRAGMENT_CHATLIST);
                    return;
            }
        } else if (args == null) {
            // Retain old arguments.
            args = fragment.getArguments();
        }

        if (args != null) {
            if (fragment.getArguments() != null) {
                fragment.getArguments().putAll(args);
            } else {
                fragment.setArguments(args);
            }
        }

        try {
            FragmentTransaction trx = fm.beginTransaction();
            trx.replace(R.id.contentFragment, fragment, tag)
                    .addToBackStack(tag)
                    .setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
                    .commitAllowingStateLoss();
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to navigate to fragment " + tag + "; falling back to Chats", e);
            showMainTab(FRAGMENT_CHATLIST);
        }
    }

    private void recoverToLogin() {
        if (isFinishing() || isDestroyed()) {
            return;
        }
        try {
            Intent fallback = new Intent(this, LoginActivity.class);
            fallback.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TASK
                    | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(fallback);
        } catch (Throwable t) {
            Log.e(TAG, "Unable to launch login recovery", t);
        } finally {
            finish();
        }
    }

    @Override
    public void toggleProgressIndicator(boolean on) {
        List<Fragment> fragments = getSupportFragmentManager().getFragments();
        for (Fragment f : fragments) {
            if (f instanceof UiUtils.ProgressIndicator && (f.isVisible() || !on)) {
                ((UiUtils.ProgressIndicator) f).toggleProgressIndicator(on);
            }
        }
    }

    @Override
    public void onAcceptAvatar(String topicName, Bitmap avatar) {
        if (isDestroyed() || isFinishing()) {
            return;
        }

        UiUtils.updateAvatar(Cache.getTinode().getMeTopic(), avatar);
    }

    interface FormUpdatable {
        void updateFormValues(final FragmentActivity activity, final MeTopic<VxCard> me);
    }

    // This is called on Websocket thread.
    private class MeListener extends UiUtils.MeEventListener {
        private void updateVisibleInfoFragment() {
            runOnUiThread(() -> {
                List<Fragment> fragments = getSupportFragmentManager().getFragments();
                for (Fragment f : fragments) {
                    if (f != null && f.isVisible() && f instanceof FormUpdatable) {
                        ((FormUpdatable) f).updateFormValues(ChatsActivity.this, mMeTopic);
                    }
                }
            });
        }

        @Override
        public void onInfo(MsgServerInfo info) {
            datasetChanged();
        }

        @Override
        public void onPres(MsgServerPres pres) {
            if ("msg".equals(pres.what)) {
                datasetChanged();
            } else if ("off".equals(pres.what) || "on".equals(pres.what)) {
                datasetChanged();
            }
        }

        @Override
        public void onMetaSub(final Subscription<VxCard, PrivateType> sub) {
            if (sub.deleted == null) {
                if (sub.pub != null) {
                    sub.pub.constructBitmap();
                }

                if (!UiUtils.isPermissionGranted(ChatsActivity.this, Manifest.permission.WRITE_CONTACTS)) {
                    // We can't save contact if we don't have appropriate permission.
                    return;
                }

                Tinode tinode = Cache.getTinode();
                if (mAccount == null) {
                    mAccount = Utils.getSavedAccount(AccountManager.get(ChatsActivity.this), tinode.getMyId());
                }
                if (Topic.isP2PType(sub.topic)) {
                    ContactsManager.processContact(ChatsActivity.this,
                            ChatsActivity.this.getContentResolver(), mAccount, tinode,
                            sub.pub, null, sub.getUnique(), sub.deleted != null,
                            null, false);
                }
            }
        }

        @Override
        public void onMetaDesc(final Description<VxCard, PrivateType> desc) {
            if (desc.pub != null) {
                desc.pub.constructBitmap();
            }

            updateVisibleInfoFragment();
        }

        @Override
        public void onSubsUpdated() {
            datasetChanged();
        }

        @Override
        public void onSubscriptionError(Exception ex) {
            runOnUiThread(() -> {
                Fragment fragment = UiUtils.getVisibleFragment(getSupportFragmentManager());
                if (fragment instanceof UiUtils.ProgressIndicator) {
                    ((UiUtils.ProgressIndicator) fragment).toggleProgressIndicator(false);
                }
            });
        }

        @Override
        public void onContUpdated(final String contact) {
            datasetChanged();
        }

        @Override
        public void onMetaTags(String[] tags) {
            updateVisibleInfoFragment();
        }

        @Override
        public void onCredUpdated(Credential[] cred) {
            updateVisibleInfoFragment();
        }
    }

    private class ContactsEventListener extends UiUtils.EventListener {
        ContactsEventListener(boolean online) {
            super(ChatsActivity.this, online);
        }

        @Override
        public void onLogin(int code, String txt) {
            super.onLogin(code, txt);
            if (mMeTopicListener != null) {
                try {
                    UiUtils.attachMeTopic(ChatsActivity.this, mMeTopicListener);
                } catch (Throwable t) {
                    Log.e(TAG, "Failed to attach MeTopic after login", t);
                }
            }
        }

        @Override
        public void onDisconnect(boolean byServer, int code, String reason) {
            super.onDisconnect(byServer, code, reason);

            // Update online status of contacts.
            datasetChanged();
        }
    }
}
