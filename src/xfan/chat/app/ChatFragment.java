package xfan.chat.app;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ChatFragment extends Fragment {

    public MainActivity host;
    private ListView lv;
    private EditText edMsg;
    private TextView tvOnline;
    private ProgressBar pbSending;
    private View inputArea;
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
    private volatile boolean polling = false;

    private static final int REQ_PICK = 101;
    private static final int REQ_PICK_AVATAR = 102;

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle b) {
        View v = inf.inflate(R.layout.frag_chat, c, false);
        lv = v.findViewById(R.id.lvMsg);
        edMsg = v.findViewById(R.id.edMsg);
        tvOnline = v.findViewById(R.id.tvOnline);
        pbSending = v.findViewById(R.id.pbSending);
        inputArea = v.findViewById(R.id.inputArea);
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
                if ("image".equals(m.type) && !m.content.isEmpty()) openImage(m.content);
            }
        });

        applyTheme();
        return v;
    }

    /** 应用主题到本页面（输入区 / 在线条） */
    public void applyTheme() {
        if (inputArea == null) return;
        try {
            inputArea.setBackgroundColor(ThemeEngine.themePanel());
            if (tvOnline != null) {
                tvOnline.setBackgroundColor(ThemeEngine.themePanel());
                tvOnline.setTextColor(ThemeEngine.themeTextSub());
            }
            if (edMsg != null) edMsg.setTextColor(ThemeEngine.themeText());
            if (ad != null) ad.notifyDataSetChanged();
        } catch (Exception ignore) {}
    }

    /**
     * UI 是否可用。
     * fragment 可能在 onCreateView 之前就被 MainActivity 触发加载（切页/拉取资料），
     * 那时 ad / lv 还是 null，直接访问会空指针闪退。
     */
    private boolean ready() {
        return isAdded() && getActivity() != null && getView() != null
                && ad != null && lv != null;
    }

    /** 在 UI 线程执行，自动跳过不可用状态 */
    private void ui(Runnable r) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            public void run() {
                if (!ready()) return;
                r.run();
            }
        });
    }

    public void onShow() {
        visible = true;
        applyTheme();
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
    public void onResume() {
        super.onResume();
        if (!isHidden()) { visible = true; startPoll(); }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPoll();
    }

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
        if (!ready()) return;
        // 若还没打开任何会话，默认进入第一个频道
        if (channelCode.isEmpty() && peer.isEmpty()) {
            JSONArray chs = r.optJSONArray("channels");
            if (chs != null && chs.length() > 0) {
                String first = String.valueOf(chs.opt(0));
                openChannel(first, "频道 " + first);
            }
        }
        applyTheme();
    }

    // ---------- 历史消息 ----------
    private void loadHistory() {
        new Thread(new Runnable() {
            public void run() {
                String url = mode.equals("channel")
                        ? ("user.php?action=ch_history&code=" + channelCode)
                        : ("user.php?action=pm_history&peer=" + Uri.encode(peer));
                final JSONObject r = Api.get(url);
                ui(new Runnable() {
                    public void run() {
                        if (r == null || !r.optBoolean("success", false)) {
                            String why = (r == null)
                                    ? ("加载失败\n" + Api.lastError)
                                    : r.optString("error", "加载失败");
                            Toast.makeText(getActivity(), why, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        JSONArray arr = r.optJSONArray("messages");
                        if (arr == null) return;
                        merge(parse(arr), true);
                    }
                });
            }
        }).start();
    }

    /** 合并消息；replace=true 表示整量替换（用于发送后/首次加载） */
    private void merge(List<Models.Msg> got, boolean replace) {
        if (!ready()) return;
        if (replace) { data.clear(); keys.clear(); }
        boolean added = false;
        for (Models.Msg m : got) {
            if (keys.contains(m.key())) continue;
            keys.add(m.key());
            data.add(m);
            if (m.timestamp > lastTs) lastTs = m.timestamp;
            added = true;
        }
        if (!added && !replace) return;
        ThemeEngine.trimRainbow(60);
        ad.sort();
        ad.notifyDataSetChanged();
        lv.setSelection(data.size() - 1);
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
        h.postDelayed(pollTask, 1200);
    }

    private void stopPoll() {
        if (pollTask != null) { h.removeCallbacks(pollTask); pollTask = null; }
        polling = false;
    }

    private void pollOnce() {
        if (channelCode.isEmpty() && peer.isEmpty()) return;
        if (polling) return;          // 上一个还没回来就跳过，避免线程堆积
        polling = true;
        new Thread(new Runnable() {
            public void run() {
                String url = mode.equals("channel")
                        ? ("user.php?action=ch_poll&code=" + channelCode + "&last=" + lastTs)
                        : ("user.php?action=pm_poll&peer=" + Uri.encode(peer) + "&last=" + lastTs);
                final JSONObject r = Api.get(url);
                // 无论成功失败都必须复位，否则轮询会永久停摆
                ui(new Runnable() {
                    public void run() {
                        try {
                            if (r == null || !r.optBoolean("success", false)) return;
                            JSONArray arr = r.optJSONArray("messages");
                            if (arr == null || arr.length() == 0) return;
                            merge(parse(arr), false);
                        } finally {
                            polling = false;
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
                ui(new Runnable() {
                    public void run() {
                        if (r == null || !r.optBoolean("success", false)) return;
                        JSONArray arr = r.optJSONArray("users");
                        if (arr == null) return;
                        StringBuilder sb = new StringBuilder("在线 " + arr.length() + "：");
                        for (int i = 0; i < arr.length() && i < 8; i++) {
                            sb.append(arr.optString(i, ""));
                            if (i < arr.length() - 1) sb.append("、");
                        }
                        if (tvOnline != null) tvOnline.setText(sb.toString());
                    }
                });
            }
        }).start();
    }

    // ---------- 发送 ----------
    private void sendText() {
        if (edMsg == null) return;
        final String txt = edMsg.getText().toString().trim();
        if (txt.isEmpty()) return;
        if (channelCode.isEmpty() && peer.isEmpty()) { toast("请先选择频道或好友"); return; }
        edMsg.setText("");
        doSend(txt, "text", "", 0);
    }

    private void setSending(final boolean on) {
        ui(new Runnable() {
            public void run() {
                if (pbSending != null) pbSending.setVisibility(on ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void doSend(final String content, final String type,
                        final String filename, final int size) {
        setSending(true);
        new Thread(new Runnable() {
            public void run() {
                JSONObject b = new JSONObject();
                try {
                    if (mode.equals("channel")) b.put("code", channelCode);
                    else b.put("peer", peer);
                    b.put("content", content);
                    b.put("type", type);
                    if (type.equals("image")) {
                        b.put("filename", filename);
                        b.put("size", size);
                    }
                } catch (Exception ignore) {}
                final JSONObject r = Api.post(
                        mode.equals("channel") ? "user.php?action=ch_send" : "user.php?action=pm_send", b);
                setSending(false);
                ui(new Runnable() {
                    public void run() {
                        if (r == null) { toast("发送失败\n" + Api.lastError); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "发送失败"));
                            return;
                        }
                        if (host != null) host.refreshPoints();
                        // 整量重拉：只靠增量轮询可能拿不回自己刚发的消息
                        loadHistory();
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
    public void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (getActivity() == null) return;
        if (res != getActivity().RESULT_OK || d == null || d.getData() == null) return;
        Uri uri = d.getData();
        String path = Resolver.getPath(getActivity(), uri);
        if (path == null || !(new File(path).exists())) { toast("无法读取该文件"); return; }
        if (req == REQ_PICK) uploadAndSend(path);
    }

    private void uploadAndSend(final String path) {
        final File f = new File(path);
        toast("上传中…");
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.upload("file.php?action=upload", f, "image",
                        new HashMap<String, String>());
                ui(new Runnable() {
                    public void run() {
                        if (r == null) { toast("上传失败（可能是服务器拦截了上传请求）"); return; }
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
        toast("读取中…");
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("user.php?action=my_uploads");
                ui(new Runnable() {
                    public void run() {
                        if (r == null) {
                            toast("获取失败\n" + Api.lastUrl + "\n" + Api.lastError);
                            return;
                        }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "获取失败"));
                            return;
                        }
                        // 三种形态都兼容：
                        // groups_list（数组，App 首选）/ groups（对象，网页端格式）/ items（扁平）
                        final List<String> urls = new ArrayList<String>();
                        final List<String> names = new ArrayList<String>();

                        JSONArray gl = r.optJSONArray("groups_list");
                        if (gl != null) {
                            for (int i = 0; i < gl.length(); i++) {
                                JSONObject day = gl.optJSONObject(i);
                                if (day == null) continue;
                                collect(day.optJSONArray("items"), urls, names);
                            }
                        }

                        JSONObject go = r.optJSONObject("groups");
                        if (go != null) {
                            java.util.Iterator<String> it = go.keys();
                            while (it.hasNext()) {
                                collect(go.optJSONArray(it.next()), urls, names);
                            }
                        }

                        collect(r.optJSONArray("items"), urls, names);
                        JSONArray flat = r.optJSONArray("items");
                        if (flat != null) {
                            for (int j = 0; j < flat.length(); j++) {
                                JSONObject it = flat.optJSONObject(j);
                                if (it == null) continue;
                                String u = it.optString("url", "");
                                if (u.isEmpty()) continue;
                                urls.add(u);
                                names.add(it.optString("name", "图片"));
                            }
                        }
                        if (urls.isEmpty()) { toast("还没有上传记录"); return; }

                        // 带缩略图的网格，点一下直接发送
                        android.widget.ScrollView sv = new android.widget.ScrollView(getActivity());
                        LinearLayout box = new LinearLayout(getActivity());
                        box.setOrientation(LinearLayout.VERTICAL);
                        box.setPadding(12, 8, 12, 8);
                        for (int i = 0; i < urls.size(); i++) {
                            final String u = urls.get(i);
                            final String nm = names.get(i);
                            LinearLayout row = new LinearLayout(getActivity());
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setGravity(Gravity.CENTER_VERTICAL);
                            row.setPadding(0, 6, 0, 6);

                            android.widget.ImageView thumb =
                                    new android.widget.ImageView(getActivity());
                            thumb.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
                            thumb.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                            new ImgLoader(thumb).load(Api.absUrl(u), 200, 200);

                            TextView t = new TextView(getActivity());
                            t.setText(nm);
                            t.setTextColor(ThemeEngine.themeText());
                            t.setPadding(12, 0, 0, 0);
                            t.setTextSize(13);

                            row.addView(thumb);
                            row.addView(t);
                            row.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View v) { doSend(u, "image", nm, 0); }
                            });
                            box.addView(row);
                        }
                        sv.addView(box);
                        new AlertDialog.Builder(getActivity())
                                .setTitle("我的上传（点击发送）")
                                .setView(sv)
                                .setNegativeButton(R.string.btn_close, null)
                                .show();
                    }
                });
            }
        }).start();
    }

    /** 从 items 数组里收集图片 url 与名称 */
    private void collect(JSONArray items, List<String> urls, List<String> names) {
        if (items == null) return;
        for (int j = 0; j < items.length(); j++) {
            JSONObject it = items.optJSONObject(j);
            if (it == null) continue;
            String u = it.optString("url", "");
            if (u.isEmpty()) u = it.optString("path", "");
            if (u.isEmpty()) continue;
            urls.add(u);
            names.add(it.optString("name", "图片"));
        }
    }

    private void openImage(String content) {
        if (getActivity() == null || content == null || content.isEmpty()) return;
        startActivity(new Intent(getActivity(), ImageViewActivity.class)
                .putExtra("url", Api.absUrl(content)));
    }

    // ---------- 消息菜单（撤回 / 复制）----------
    private void showMsgMenu(final Models.Msg m) {
        if (getActivity() == null) return;
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
                ui(new Runnable() {
                    public void run() {
                        if (r == null) { toast("网络错误"); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "撤回失败"));
                            return;
                        }
                        toast("已撤回");
                        loadHistory();
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
