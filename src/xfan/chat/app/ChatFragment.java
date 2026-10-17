package xfan.chat.app;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class ChatFragment extends Fragment {

    public MainActivity host;
    private ListView lv;
    private EditText edMsg;
    private TextView tvOnline;
    private MsgAdapter ad;
    private final List<Models.Msg> data = new ArrayList<Models.Msg>();
    private final Set<String> keys = new HashSet<String>();

    private String mode = "channel";   // channel | pm
    private String channelCode = "";
    private String channelName = "";
    private String peer = "";
    private long lastTs = 0;

    private Handler h = new Handler();
    private Runnable pollTask;
    private boolean visible = false;

    private static final int REQ_PICK = 101;
    private static final int REQ_PICK_AVATAR = 102;

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle b) {
        View v = inf.inflate(R.layout.frag_chat, c, false);
        lv = v.findViewById(R.id.lvMsg);
        edMsg = v.findViewById(R.id.edMsg);
        tvOnline = v.findViewById(R.id.tvOnline);
        Button btnSend = v.findViewById(R.id.btnSend);
        Button btnImg = v.findViewById(R.id.btnImg);
        Button btnUp = v.findViewById(R.id.btnUp);

        ad = new MsgAdapter(getActivity(), data);
        lv.setAdapter(ad);

        btnSend.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) { sendText(); }
        });
        btnImg.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) { pickImage(); }
        });
        btnUp.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) { showUploads(); }
        });

        lv.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v2, int pos, long id) {
                if (pos < 0 || pos >= data.size()) return true;
                showMsgMenu(data.get(pos));
                return true;
            }
        });

        // 图片点击 -> 全屏预览
        lv.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v2, int pos, long id) {
                if (pos < 0 || pos >= data.size()) return;
                Models.Msg m = data.get(pos);
                if ("image".equals(m.type) && !m.content.isEmpty()) {
                    startActivity(new Intent(getActivity(), ImageViewActivity.class)
                            .putExtra("url", Api.absUrl(m.content)));
                }
            }
        });
        return v;
    }

    public void onShow() {
        visible = true;
        startPoll();
        loadOnline();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        visible = !hidden;
        if (hidden) stopPoll(); else { startPoll(); loadOnline(); }
    }

    @Override
    public void onDestroy() { super.onDestroy(); stopPoll(); }

    // ---------- 会话切换 ----------
    public void openChannel(String code, String name) {
        mode = "channel";
        channelCode = code;
        channelName = name;
        peer = "";
        reset();
        loadHistory();
        if (host != null) { host.gotoChat(); host.tvTitleSet(name); }
    }

    public void openPm(String who) {
        mode = "pm";
        peer = who;
        channelCode = "";
        reset();
        loadHistory();
        if (host != null) { host.gotoChat(); host.tvTitleSet("与 " + who + " 私聊"); }
    }

    private void reset() {
        data.clear();
        keys.clear();
        lastTs = 0;
        if (ad != null) ad.notifyDataSetChanged();
    }

    public void applyProfile(JSONObject r) {
        // 首次进入：默认打开第一个频道
        if (channelCode.isEmpty() && peer.isEmpty()) {
            JSONArray chs = r.optJSONArray("channels");
            if (chs != null && chs.length() > 0) {
                String first = String.valueOf(chs.opt(0));
                openChannel(first, "频道 " + first);
            }
        }
    }

    // ---------- 历史消息 ----------
    private void loadHistory() {
        new Thread(new Runnable() {
            public void run() {
                String url = mode.equals("channel")
                        ? ("user.php?action=ch_history&code=" + channelCode)
                        : ("user.php?action=pm_history&peer=" + Uri.encode(peer));
                final JSONObject r = Api.get(url);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null || !r.optBoolean("success", false)) return;
                        JSONArray arr = r.optJSONArray("messages");
                        if (arr == null) return;
                        List<Models.Msg> got = parse(arr);
                        data.clear();
                        keys.clear();
                        for (Models.Msg m : got) {
                            keys.add(m.key());
                            data.add(m);
                            if (m.timestamp > lastTs) lastTs = m.timestamp;
                        }
                        ad.sort();
                        ad.notifyDataSetChanged();
                        lv.setSelection(data.size() - 1);
                    }
                });
            }
        }).start();
    }

    // ---------- 轮询 ----------
    private void startPoll() {
        stopPoll();
        pollTask = new Runnable() {
            public void run() {
                if (!visible || getActivity() == null) return;
                pollOnce();
                h.postDelayed(this, 4000);
            }
        };
        h.postDelayed(pollTask, 1500);
    }

    private void stopPoll() {
        if (pollTask != null) { h.removeCallbacks(pollTask); pollTask = null; }
    }

    private void pollOnce() {
        if (channelCode.isEmpty() && peer.isEmpty()) return;
        new Thread(new Runnable() {
            public void run() {
                String url = mode.equals("channel")
                        ? ("user.php?action=ch_poll&code=" + channelCode + "&last=" + lastTs)
                        : ("user.php?action=pm_poll&peer=" + Uri.encode(peer) + "&last=" + lastTs);
                final JSONObject r = Api.get(url);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) return;
                        if (!r.optBoolean("success", false)) return;
                        JSONArray arr = r.optJSONArray("messages");
                        if (arr == null || arr.length() == 0) return;
                        boolean added = false;
                        for (Models.Msg m : parse(arr)) {
                            if (keys.contains(m.key())) continue;
                            keys.add(m.key());
                            data.add(m);
                            if (m.timestamp > lastTs) lastTs = m.timestamp;
                            added = true;
                        }
                        if (added) {
                            ad.sort();
                            ad.notifyDataSetChanged();
                            lv.setSelection(data.size() - 1);
                        }
                    }
                });
            }
        }).start();
    }

    private void loadOnline() {
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("index.php?action=get_online_users");
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null || !r.optBoolean("success", false)) return;
                        JSONArray arr = r.optJSONArray("users");
                        if (arr == null) return;
                        StringBuilder sb = new StringBuilder("在线 " + arr.length() + "：");
                        for (int i = 0; i < arr.length() && i < 8; i++) {
                            sb.append(arr.optString(i, ""));
                            if (i < arr.length() - 1) sb.append("、");
                        }
                        tvOnline.setText(sb.toString());
                    }
                });
            }
        }).start();
    }

    // ---------- 发送 ----------
    private void sendText() {
        final String txt = edMsg.getText().toString().trim();
        if (txt.isEmpty()) return;
        if (channelCode.isEmpty() && peer.isEmpty()) { toast("请先选择频道或好友"); return; }
        edMsg.setText("");
        doSend(txt, "text", "", 0);
    }

    private void doSend(final String content, final String type,
                        final String filename, final int size) {
        new Thread(new Runnable() {
            public void run() {
                JSONObject b = new JSONObject();
                try {
                    if (mode.equals("channel")) {
                        b.put("code", channelCode);
                    } else {
                        b.put("peer", peer);
                    }
                    b.put("content", content);
                    b.put("type", type);
                    if (type.equals("image")) {
                        b.put("filename", filename);
                        b.put("size", size);
                    }
                } catch (Exception ignore) {}
                final JSONObject r = Api.post(
                        mode.equals("channel") ? "user.php?action=ch_send" : "user.php?action=pm_send", b);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) { toast("网络错误"); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "发送失败"));
                            return;
                        }
                        if (host != null) host.refreshPoints();
                        pollOnce();
                    }
                });
            }
        }).start();
    }

    // ---------- 图片 ----------
    private void pickImage() {
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_PICK);
    }

    @Override
    public void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != getActivity().RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        String path = Resolver.getPath(getActivity(), uri);
        if (path == null || !(new File(path).exists())) { toast("无法读取该文件"); return; }
        if (req == REQ_PICK) uploadAndSend(path);
    }

    private void uploadAndSend(final String path) {
        final File f = new File(path);
        new Thread(new Runnable() {
            public void run() {
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() { toast("上传中…"); }
                });
                final JSONObject r = Api.upload("file.php?action=upload", f, "image",
                        new HashMap<String, String>());
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) { toast("上传失败"); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "上传失败"));
                            return;
                        }
                        toast("上传完成，消耗 " + r.optInt("cost", 0) + " 积分");
                        doSend(r.optString("url", ""), "image",
                                r.optString("filename", ""), r.optInt("size", 0));
                    }
                });
            }
        }).start();
    }

    // ---------- 我的上传 ----------
    private void showUploads() {
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("user.php?action=my_uploads");
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) { toast("网络错误"); return; }
                        JSONArray g = r.optJSONArray("groups");
                        if (g == null || g.length() == 0) { toast("还没有上传记录"); return; }
                        final List<String> urls = new ArrayList<String>();
                        final List<String> names = new ArrayList<String>();
                        for (int i = 0; i < g.length(); i++) {
                            JSONObject day = g.optJSONObject(i);
                            if (day == null) continue;
                            JSONArray items = day.optJSONArray("items");
                            if (items == null) continue;
                            for (int j = 0; j < items.length(); j++) {
                                JSONObject it = items.optJSONObject(j);
                                if (it == null) continue;
                                String u = it.optString("url", "");
                                if (u.isEmpty()) continue;
                                urls.add(u);
                                names.add(it.optString("name", "图片"));
                            }
                        }
                        if (urls.isEmpty()) { toast("还没有上传记录"); return; }
                        new AlertDialog.Builder(getActivity())
                                .setTitle("我的上传（点击发送）")
                                .setItems(names.toArray(new String[0]),
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface d, int w) {
                                                String u = urls.get(w);
                                                doSend(u, "image",
                                                        names.get(w), 0);
                                            }
                                        })
                                .show();
                    }
                });
            }
        }).start();
    }

    // ---------- 消息菜单（撤回 / 复制）----------
    private void showMsgMenu(final Models.Msg m) {
        List<String> acts = new ArrayList<String>();
        acts.add("复制");
        boolean mine = m.username.equals(Prefs.user());
        if (mine && (System.currentTimeMillis() / 1000 - m.timestamp) < 120) acts.add("撤回");
        new AlertDialog.Builder(getActivity())
                .setItems(acts.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        if (w == 0) {
                            android.content.ClipboardManager cm =
                                    (android.content.ClipboardManager) getActivity()
                                            .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                            cm.setText(m.content);
                            toast("已复制");
                        } else {
                            doRecall(m);
                        }
                    }
                }).show();
    }

    private void doRecall(final Models.Msg m) {
        new Thread(new Runnable() {
            public void run() {
                JSONObject b = new JSONObject();
                try {
                    b.put("timestamp", m.timestamp);
                    b.put("content", m.content);
                    if (mode.equals("channel")) b.put("code", channelCode);
                    else b.put("peer", peer);
                } catch (Exception ignore) {}
                final JSONObject r = Api.post("index.php?action=recall", b);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) { toast("网络错误"); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "撤回失败"));
                            return;
                        }
                        toast("已撤回");
                        data.remove(m);
                        keys.remove(m.key());
                        ad.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    // ---------- 工具 ----------
    private List<Models.Msg> parse(JSONArray arr) {
        List<Models.Msg> out = new ArrayList<Models.Msg>();
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) continue;
            Models.Msg m = new Models.Msg();
            m.username = o.optString("username", "");
            if (m.username.isEmpty()) m.username = o.optString("from", "");
            m.content = o.optString("content", "");
            m.type = o.optString("type", "text");
            m.timestamp = o.optLong("timestamp", 0);
            m.avatar = o.optString("avatar", "");
            m.dup = o.optInt("dup", 0);
            m.id = o.optInt("id", 0);
            m.badge = o.optString("badge", "");
            out.add(m);
        }
        return out;
    }

    private void toast(String s) {
        if (getActivity() == null) return;
        Toast.makeText(getActivity(), s, Toast.LENGTH_SHORT).show();
    }
}
