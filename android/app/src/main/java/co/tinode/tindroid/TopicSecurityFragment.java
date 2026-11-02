package co.tinode.tindroid;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Collection;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import co.tinode.tindroid.media.VxCard;
import co.tinode.tinodesdk.ComTopic;
import co.tinode.tinodesdk.PromisedReply;
import co.tinode.tinodesdk.Tinode;
import co.tinode.tinodesdk.model.Acs;
import co.tinode.tinodesdk.model.Drafty;
import co.tinode.tinodesdk.model.ServerMessage;
import co.tinode.tinodesdk.model.Subscription;

/**
 * Topic permissions fragment: p2p or a group topic.
 */
public class TopicSecurityFragment extends Fragment implements MessageActivity.DataSetChangeListener {

    private static final String TAG = "TopicPermissionsFrag";

    private static final int ACTION_DELETE = 1;
    private static final int ACTION_LEAVE = 2;
    private static final int ACTION_REPORT = 3;
    private static final int ACTION_BAN_TOPIC = 4;
    private static final int ACTION_DELMSG = 5;

    private ComTopic<VxCard> mTopic;
    private PromisedReply.FailureListener<ServerMessage> mFailureListener;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_tpc_security, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View fragment, Bundle savedInstanceState) {
        final Activity activity = requireActivity();

        Toolbar toolbar = activity.findViewById(R.id.toolbar);
        toolbar.setTitle(R.string.topic_settings);
        toolbar.setSubtitle(null);
        toolbar.setLogo(null);

        mFailureListener = new UiUtils.ToastFailureListener(activity);

        fragment.findViewById(R.id.permissionsSingle).setOnClickListener(v ->
                UiUtils.showEditPermissions(activity, mTopic, mTopic.getAccessMode().getWant(), null,
                    Const.ACTION_UPDATE_SELF_SUB,
                    mTopic.getAccessMode().getGivenHelper().isOwner() ? "" : "O"));

        fragment.findViewById(R.id.authPermissions).setOnClickListener(v ->
                UiUtils.showEditPermissions(activity, mTopic, mTopic.getAuthAcsStr(), null,
                        Const.ACTION_UPDATE_AUTH, "O"));

        fragment.findViewById(R.id.anonPermissions).setOnClickListener(v ->
                UiUtils.showEditPermissions(activity, mTopic, mTopic.getAnonAcsStr(), null,
                        Const.ACTION_UPDATE_ANON, "O"));

        fragment.findViewById(R.id.userOne).setOnClickListener(v ->
                UiUtils.showEditPermissions(activity, mTopic,
                        mTopic.getAccessMode().getWant(), null,
                        Const.ACTION_UPDATE_SELF_SUB, "ASDO"));

        fragment.findViewById(R.id.userTwo).setOnClickListener(v ->
                UiUtils.showEditPermissions(activity, mTopic,
                        mTopic.getSubscription(mTopic.getName()).acs.getGiven(),
                        mTopic.getName(),
                        Const.ACTION_UPDATE_SUB, "ASDO"));

        fragment.findViewById(R.id.buttonClearMessages).setOnClickListener(v -> {
            int confirm = mTopic.isDeleter() ? R.string.confirm_delmsg_for_all : R.string.confirm_delmsg_for_self;
            showConfirmationDialog(null, R.string.clear_messages, confirm, ACTION_DELMSG);
        });

        fragment.findViewById(R.id.buttonLeave).setOnClickListener(v ->
                showConfirmationDialog(null, R.string.leave_conversation,
                        R.string.confirm_leave_topic, ACTION_LEAVE));

        fragment.findViewById(R.id.buttonDeleteGroup).setOnClickListener(v ->
                showConfirmationDialog(null, R.string.delete_group,
                        R.string.confirm_delete_topic, ACTION_DELETE));

        fragment.findViewById(R.id.buttonBlock).setOnClickListener(view12 -> {
            VxCard pub = mTopic.getPub();
            String topicTitle = pub != null ? pub.fn : null;
            topicTitle = TextUtils.isEmpty(topicTitle) ?
                    activity.getString(R.string.placeholder_topic_title) : topicTitle;
            showConfirmationDialog(topicTitle, R.string.block_contact,
                    R.string.confirm_contact_ban, ACTION_BAN_TOPIC);
        });

        final View.OnClickListener reportListener = view -> showReportReasonDialog();
        fragment.findViewById(R.id.buttonReportContact).setOnClickListener(reportListener);
        fragment.findViewById(R.id.buttonReportGroup).setOnClickListener(reportListener);
        fragment.findViewById(R.id.buttonReportChannel).setOnClickListener(reportListener);
    }

    private void showReportReasonDialog() {
        final FragmentActivity activity = requireActivity();
        if (mTopic == null || activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        final String[] labels = new String[]{
                activity.getString(R.string.ts_report_reason_scam),
                activity.getString(R.string.ts_report_reason_harassment),
                activity.getString(R.string.ts_report_reason_impersonation),
                activity.getString(R.string.ts_report_reason_spam),
                activity.getString(R.string.ts_report_reason_other)
        };
        final String[] values = new String[]{"SCAM", "HARASSMENT", "IMPERSONATION", "SPAM", "OTHER"};

        new AlertDialog.Builder(activity)
                .setTitle(R.string.ts_report_reason_title)
                .setItems(labels, (dialog, which) -> showReportDetailsDialog(values[which]))
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    private void showReportDetailsDialog(String reason) {
        final FragmentActivity activity = requireActivity();
        final EditText input = new EditText(activity);
        input.setHint(R.string.ts_report_details_hint);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES |
                InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setMinLines(3);
        input.setMaxLines(6);
        int pad = Math.round(20 * getResources().getDisplayMetrics().density);
        input.setPadding(pad, pad / 2, pad, pad / 2);

        new AlertDialog.Builder(activity)
                .setTitle(R.string.ts_report_details_title)
                .setView(input)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.ts_report_action, (dialog, which) ->
                        submitModerationReport(reason, input.getText() == null ? "" : input.getText().toString()))
                .show();
    }

    @SuppressWarnings("unchecked")
    private String reportTargetId() {
        if (mTopic == null) {
            return null;
        }
        if (mTopic.isP2PType()) {
            Collection<Subscription<VxCard, ?>> subs = (Collection<Subscription<VxCard, ?>>) (Collection<?>) mTopic.getSubscriptions();
            if (subs != null) {
                for (Subscription<VxCard, ?> sub : subs) {
                    if (sub != null && !TextUtils.isEmpty(sub.user) && !Cache.getTinode().isMe(sub.user)) {
                        return sub.user;
                    }
                }
            }
        }
        // Groups/channels are moderated by topic id.
        return mTopic.getName();
    }

    private void submitModerationReport(String reason, String details) {
        final FragmentActivity activity = requireActivity();
        final String target = reportTargetId();
        final String topicName = mTopic != null ? mTopic.getName() : "";

        ModerationApiClient.submit(target, topicName, reason, details, new ModerationApiClient.Callback() {
            @Override
            public void onSuccess(String reportId) {
                Toast.makeText(activity, R.string.ts_report_submitted, Toast.LENGTH_SHORT).show();
                performTinodeReportAndBlock();
            }

            @Override
            public void onFailure(String message) {
                Toast.makeText(activity, R.string.ts_report_saved_failed, Toast.LENGTH_LONG).show();
                performTinodeReportAndBlock();
            }
        });
    }

    private void performTinodeReportAndBlock() {
        if (mTopic == null) {
            return;
        }
        HashMap<String, Object> json = new HashMap<>();
        json.put("action", "report");
        json.put("target", mTopic.getName());
        Drafty msg = new Drafty().attachJSON(json);
        HashMap<String,Object> head = new HashMap<>();
        head.put("mime", Drafty.MIME_TYPE);
        Cache.getTinode().publish(Tinode.TOPIC_SYS, msg, head, null);

        mTopic.updateMode(null, "-JP")
                .thenApply(new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        Activity activity = getActivity();
                        if (activity != null && !activity.isFinishing()) {
                            Intent intent = new Intent(activity, ChatsActivity.class);
                            intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                            startActivity(intent);
                            activity.finish();
                        }
                        return null;
                    }
                }).thenCatch(mFailureListener);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void onStart() {
        final Bundle args = getArguments();
        if (args == null) {
            return;
        }

        final Activity activity = requireActivity();
        final View fragment = getView();
        if (fragment == null) {
            activity.finish();
            return;
        }

        String name = args.getString(Const.INTENT_EXTRA_TOPIC);
        name = Tinode.parseTinodeUrl(name);
        mTopic = (ComTopic<VxCard>) Cache.getTinode().getTopic(name);
        if (mTopic == null) {
            Log.d(TAG, "TopicPermissions resumed with null topic.");
            activity.finish();
            return;
        }

        final View defaultPermissions = fragment.findViewById(R.id.defaultPermissionsWrapper);
        final View deleteGroup = fragment.findViewById(R.id.buttonDeleteGroup);
        final View blockContact = fragment.findViewById(R.id.buttonBlock);
        final View reportGroup = fragment.findViewById(R.id.buttonReportGroup);
        final View reportChannel = fragment.findViewById(R.id.buttonReportChannel);
        final View reportContact = fragment.findViewById(R.id.buttonReportContact);

        if (mTopic.isGrpType()) {
            final View buttonLeave = fragment.findViewById(R.id.buttonLeave);
            fragment.findViewById(R.id.singleUserPermissionsWrapper).setVisibility(View.VISIBLE);
            fragment.findViewById(R.id.p2pPermissionsWrapper).setVisibility(View.GONE);
            blockContact.setVisibility(View.GONE);
            reportContact.setVisibility(View.GONE);

            if (mTopic.isOwner()) {
                buttonLeave.setVisibility(View.GONE);
                reportGroup.setVisibility(View.GONE);
                reportChannel.setVisibility(View.GONE);
                deleteGroup.setVisibility(View.VISIBLE);
            } else {
                buttonLeave.setVisibility(View.VISIBLE);
                deleteGroup.setVisibility(View.GONE);
                if (mTopic.isChannel()) {
                    reportGroup.setVisibility(View.GONE);
                    reportChannel.setVisibility(View.VISIBLE);
                } else {
                    reportGroup.setVisibility(View.VISIBLE);
                    reportChannel.setVisibility(View.GONE);
                }
            }
            defaultPermissions.setVisibility(mTopic.isManager() ? View.VISIBLE : View.GONE);

        } else if (mTopic.isP2PType()) {
            fragment.findViewById(R.id.singleUserPermissionsWrapper).setVisibility(View.GONE);
            fragment.findViewById(R.id.p2pPermissionsWrapper).setVisibility(View.VISIBLE);

            VxCard two = mTopic.getPub();
            ((TextView) fragment.findViewById(R.id.userTwoLabel)).setText(two != null && two.fn != null ?
                    two.fn : mTopic.getName());

            defaultPermissions.setVisibility(View.GONE);
            deleteGroup.setVisibility(View.GONE);
            reportGroup.setVisibility(View.GONE);
            reportChannel.setVisibility(View.GONE);
            reportContact.setVisibility(View.VISIBLE);
            blockContact.setVisibility(View.VISIBLE);
        } else {
            fragment.findViewById(R.id.singleUserPermissionsWrapper).setVisibility(View.GONE);
            fragment.findViewById(R.id.p2pPermissionsWrapper).setVisibility(View.GONE);
            defaultPermissions.setVisibility(View.GONE);
            deleteGroup.setVisibility(View.GONE);
            reportGroup.setVisibility(View.GONE);
            reportChannel.setVisibility(View.GONE);
            reportContact.setVisibility(View.GONE);
            blockContact.setVisibility(View.GONE);
        }

        notifyContentChanged();
        notifyDataSetChanged();
        super.onStart();
    }

    public void notifyDataSetChanged() {
        if (mTopic.isGrpType()) {
            return;
        }
        View fragment = getView();
        if (fragment == null) {
            return;
        }
        Acs acs = mTopic.getAccessMode();
        if (acs != null) {
            ((TextView) fragment.findViewById(R.id.userOne)).setText(acs.getWant());
        }
        Subscription sub = mTopic.getSubscription(mTopic.getName());
        if (sub != null && sub.acs != null) {
            ((TextView) fragment.findViewById(R.id.userTwo)).setText(sub.acs.getGiven());
        }
    }

    private void notifyContentChanged() {
        View fragment = getView();
        if (fragment == null) {
            return;
        }
        Acs acs = mTopic.getAccessMode();
        ((TextView) fragment.findViewById(R.id.permissionsSingle)).setText(acs == null ? "" : acs.getMode());
        ((TextView) fragment.findViewById(R.id.authPermissions)).setText(mTopic.getAuthAcsStr());
        ((TextView) fragment.findViewById(R.id.anonPermissions)).setText(mTopic.getAnonAcsStr());
    }

    private void showConfirmationDialog(final String arg1,
                                        int title_id, int message_id,
                                        final int what) {
        final FragmentActivity activity = requireActivity();
        if (activity.isFinishing() || activity.isDestroyed()) {
            return;
        }

        final AlertDialog.Builder confirmBuilder = new AlertDialog.Builder(activity);
        confirmBuilder.setNegativeButton(android.R.string.no, null);
        if (title_id != 0) {
            confirmBuilder.setTitle(title_id);
        }
        String message = activity.getString(message_id, arg1);
        confirmBuilder.setMessage(message);

        confirmBuilder.setPositiveButton(android.R.string.yes, (dialog, which) -> {
            PromisedReply<ServerMessage> response = null;
            switch (what) {
                case ACTION_LEAVE:
                    response = mTopic.delete(true);
                    break;
                case ACTION_REPORT:
                    performTinodeReportAndBlock();
                    return;
                case ACTION_BAN_TOPIC:
                    response = mTopic.updateMode(null, "-JP");
                    break;
                case ACTION_DELMSG:
                    response = mTopic.delMessages(true);
            }

            if (response != null) {
                response.thenApply(new PromisedReply.SuccessListener<>() {
                    @Override
                    public PromisedReply<ServerMessage> onSuccess(ServerMessage result) {
                        Intent intent = new Intent(activity, ChatsActivity.class);
                        intent.addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
                        startActivity(intent);
                        activity.finish();
                        return null;
                    }
                }).thenCatch(mFailureListener);
            }
        });
        confirmBuilder.show();
    }
}
