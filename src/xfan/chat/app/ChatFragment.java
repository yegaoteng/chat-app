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
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ImageView;
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
    private ViewGroup tvOnline;
    private View chatBgBase = null;
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

    private Handler h = new Handler(android.os.Looper.getMainLooper());
    private Runnable pollTask;
    private boolean visible = false;
    private volatile boolean polling = false;

    private static final int REQ_PICK = 101;
    private static final int REQ_PICK_AVATAR = 102;
    private static final int REQ_PICK_BG = 103;
    private ImageView ivChatBg;

    /** 个性化切换后调用：清空并重新拉取，让新气泡/名称色立刻可见 */
    public void reloadForStyleChange() {
        try {
            lastTs = 0;
            data.clear();
            keys.clear();
            if (ad != null) ad.notifyDataSetChanged();
            loadHistory();
        } catch (Throwable ignore) {}
    }

    /** 当前打开的「我的上传」弹窗，删除后可直接关掉重开 */
    private AlertDialog uploadDlg = null;

    /** 最近一次长按的坐标，供菜单定位 */
    private float lastTouchX = 0f, lastTouchY = 0f;

    /** 当前正在引用的消息 */
    private Models.Msg quoting = null;
    private View quoteBar;
    private TextView quoteText;
    private View loadingBar;

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle b) {
        View v = inf.inflate(R.layout.frag_chat, c, false);
        lv = v.findViewById(R.id.lvMsg);
        edMsg = v.findViewById(R.id.edMsg);
        tvOnline = v.findViewById(R.id.tvOnline);
        ivChatBg = v.findViewById(R.id.ivChatBg);
        chatBgBase = v.findViewById(R.id.chatBgBase);
        pbSending = v.findViewById(R.id.pbSending);
        inputArea = v.findViewById(R.id.inputArea);
        loadingBar = v.findViewById(R.id.loadingBar);
        quoteBar = v.findViewById(R.id.quoteBar);
        quoteText = v.findViewById(R.id.tvQuote);
        View btnQuoteCancel = v.findViewById(R.id.btnQuoteCancel);
        if (btnQuoteCancel != null) {
            btnQuoteCancel.setOnClickListener(new View.OnClickListener() {
                public void onClick(View v2) { clearQuote(); }
            });
        }
        Button btnSend = v.findViewById(R.id.btnSend);
        Button btnImg = v.findViewById(R.id.btnImg);
        Button btnUp = v.findViewById(R.id.btnUp);

        ad = new MsgAdapter(getActivity(), data);
        ad.setOnImageClick(new MsgAdapter.OnImageClick() {
            public void onImage(String content) { openImage(content); }
        });
        ad.setOnAvatarClick(new MsgAdapter.OnAvatarClick() {
            public void onAvatar(String username) { UserInfoDialog.show(ChatFragment.this, username); }
        });
        // 图片消息的长按由 adapter 转发（图片可点击后 ListView 收不到长按）
        ad.setOnItemLongClick(new MsgAdapter.OnItemLongClick() {
            public void onLongClick(int pos) {
                if (pos < 0 || pos >= data.size()) return;
                showMsgMenu(data.get(pos));
            }
        });
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

        lv.setOnTouchListener(new View.OnTouchListener() {
            public boolean onTouch(View vv, MotionEvent ev) {
                lastTouchX = ev.getX();
                lastTouchY = ev.getY();
                return false;
            }
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

    /** 切换频道/私聊时显示「聊天数据加载中…」 */
    private void showLoading(final boolean on) {
        ui(new Runnable() {
            public void run() {
                if (loadingBar != null) loadingBar.setVisibility(on ? View.VISIBLE : View.GONE);
            }
        });
    }

    /** 应用主题到本页面（输入区 / 在线条） */
    public void applyTheme() {
        if (inputArea == null) return;
        try {
            // 输入区保持透明，让聊天背景透出来（之前整块盖色，壁纸被挡住一截）
            inputArea.setBackgroundColor(0x00000000);
            if (tvOnline != null) {
                tvOnline.setBackgroundColor(ThemeEngine.themePanel());
            }
            if (edMsg != null) edMsg.setTextColor(ThemeEngine.themeText());
            if (ad != null) ad.notifyDataSetChanged();
        } catch (Exception ignore) {}
    }

    /** UI 是否可用。 */
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
        loadChatBg();
        channelName = name;
        peer = "";
        clearQuote();
        reset();
        showLoading(true);
        loadHistory();
        if (host != null) { host.gotoChat(); host.tvTitleSet(name); }
    }

    public void openPm(String who) {
        mode = "pm";
        peer = who;
        channelCode = "";
        loadChatBg();
        clearQuote();
        reset();
        showLoading(true);
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
                            showLoading(false);
                            String why = (r == null)
                                    ? ("加载失败\n" + Api.lastError)
                                    : r.optString("error", "加载失败");
                            Toast.makeText(getActivity(), why, Toast.LENGTH_SHORT).show();
                            return;
                        }
                        showLoading(false);
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
        fillQuote(data);
        ThemeEngine.trimRainbow(60);
        ad.sort();
        ad.notifyDataSetChanged();
        // 新消息从底部平滑上移，而不是突然出现
        if (!replace && added && atBottom()) {
            animateNewMessage();
        }
        lv.setSelection(data.size() - 1);
    }

    /** 列表是否已贴近底部（贴近时才播动画，避免打断正在翻记录的用户） */
    private boolean atBottom() {
        if (lv == null || lv.getAdapter() == null) return false;
        int last = lv.getAdapter().getCount() - 1;
        return lv.getLastVisiblePosition() >= last - 1;
    }

    /** 最后一条消息从输入框下方平滑上移 */
    private void animateNewMessage() {
        if (lv == null) return;
        try {
            View child = lv.getChildAt(lv.getChildCount() - 1);
            if (child == null) return;
            android.view.animation.AnimationSet set =
                    new android.view.animation.AnimationSet(true);
            android.view.animation.TranslateAnimation ta =
                    new android.view.animation.TranslateAnimation(
                            android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                            android.view.animation.Animation.RELATIVE_TO_SELF, 0f,
                            android.view.animation.Animation.RELATIVE_TO_SELF, 0.35f,
                            android.view.animation.Animation.RELATIVE_TO_SELF, 0f);
            android.view.animation.AlphaAnimation aa =
                    new android.view.animation.AlphaAnimation(0f, 1f);
            set.addAnimation(ta);
            set.addAnimation(aa);
            set.setDuration(260);
            set.setInterpolator(new android.view.animation.DecelerateInterpolator());
            child.startAnimation(set);
        } catch (Throwable ignore) {}
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

                        // 服务端同时返回 colors：username -> namecolor。
                        // 每个在线昵称要显示「他自己的」个性化颜色，
                        // 否则别人看到的都是默认色。
                        JSONObject colors = r.optJSONObject("colors");
                        final java.util.Map<String, String> cmap =
                                new java.util.HashMap<String, String>();
                        if (colors != null) {
                            java.util.Iterator<String> it = colors.keys();
                            while (it.hasNext()) {
                                String k = it.next();
                                cmap.put(k, colors.optString(k, "default"));
                            }
                        }

                        renderOnline(arr, cmap);
                    }
                });
            }
        }).start();
    }

    /**
     * 在线条：每个用户一个独立 TextView。
     * 之前整条用一个 SpannableString，彩虹只能取静态单色、也没法流动；
     * 拆成独立 View 后，各自的 namecolor（含动态七彩）才能生效。
     */
    private void renderOnline(JSONArray arr, java.util.Map<String, String> cmap) {
        if (tvOnline == null) return;
        try {
            tvOnline.removeAllViews();
            android.content.Context ctx = getActivity();
            if (ctx == null) return;

            TextView head = new TextView(ctx);
            head.setText("在线 " + arr.length() + "：");
            head.setTextColor(0xFF2E7D32);   // 「在线 N」绿色
            head.setTextSize(11);
            head.setSingleLine(true);
            tvOnline.addView(head);

            int n = Math.min(arr.length(), 8);
            for (int i = 0; i < n; i++) {
                String nm = arr.optString(i, "");
                if (nm.isEmpty()) continue;
                TextView t = new TextView(ctx);
                t.setText(nm + (i < n - 1 ? "、" : ""));
                t.setTextSize(11);
                t.setSingleLine(true);
                String key = (cmap != null && cmap.containsKey(nm)) ? cmap.get(nm) : "default";
                // 传对方自己的 key：dynamic 才会流动
                ThemeEngine.applyNameColorFor(t, key);
                tvOnline.addView(t);
            }
        } catch (Throwable ignore) {}
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
                    if (quoting != null && quoting.id > 0) b.put("reply_to", quoting.id);
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
                        clearQuote();
                        // 整量重拉：只靠增量轮询可能拿不回自己刚发的消息
                        loadHistory();
                    }
                });
            }
        }).start();
    }

    // ---------- 图片 ----------
    /** 点「发送图片」先弹窗：显示当前积分与计费规则，再选图 */
    private void pickImage() {
        if (getActivity() == null) return;

        LinearLayout box = new LinearLayout(getActivity());
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(40, 24, 40, 12);

        TextView tvPts = new TextView(getActivity());
        tvPts.setText("当前积分：" + Prefs.points() + " 分");
        tvPts.setTextSize(14);
        tvPts.setTextColor(ThemeEngine.themeText());
        tvPts.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView tvRule = new TextView(getActivity());
        tvRule.setText("普通图片：按体积计费\n"
                + "动图（GIF/WEBP）：每 KB 消耗 2 积分\n"
                + "删除已上传图片：返还 50% 积分");
        tvRule.setTextSize(12);
        tvRule.setTextColor(ThemeEngine.themeTextSub());
        tvRule.setPadding(0, 8, 0, 6);

        box.addView(tvPts);
        box.addView(tvRule);

        new AlertDialog.Builder(getActivity())
                .setTitle("发送图片")
                .setView(box)
                .setNegativeButton(R.string.btn_close, null)
                .setPositiveButton("选择图片", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
                        // 同时允许静态图与动图
                        i.setType("image/*");
                        i.addCategory(Intent.CATEGORY_OPENABLE);
                        startActivityForResult(i, REQ_PICK);
                    }
                })
                .show();
    }

    @Override
    public void onActivityResult(int req, int res, Intent d) {
        super.onActivityResult(req, res, d);
        if (getActivity() == null) return;
        if (res != getActivity().RESULT_OK || d == null || d.getData() == null) return;
        Uri uri = d.getData();
        String path = Resolver.getPath(getActivity(), uri);
        if (path == null || !(new File(path).exists())) { toast("无法读取该文件"); return; }
        if (req == REQ_PICK_BG) { uploadChatBg(path); return; }
        // 选完图先报价确认，不立即上传
        if (req == REQ_PICK) quoteThenUpload(path);
    }

    /** 选完图 -> 先问服务器扣多少分 -> 用户确认才真正上传 */
    private void quoteThenUpload(final String path) {
        final File f = new File(path);
        final long size = f.length();
        String ext = "";
        int dot = path.lastIndexOf('.');
        if (dot > 0) ext = path.substring(dot + 1).toLowerCase();

        final String ex = ext;
        toast("计算中…");
        new Thread(new Runnable() {
            public void run() {
                JSONObject q = Api.get("file.php?action=quote&size=" + size
                        + "&ext=" + java.net.URLEncoder.encode(ex));
                ui(new Runnable() {
                    public void run() {
                        if (q == null || !q.optBoolean("success", false)) {
                            // 拿不到报价就按本地估算，仍然让用户确认
                            boolean anim = "gif".equals(ex);
                            int est = (int) Math.ceil(size / (anim ? 512.0 : 1536.0));
                            confirmUpload(path, est, est / 2, -1, anim);
                            return;
                        }
                        confirmUpload(path, q.optInt("cost", 0), q.optInt("refund", 0),
                                q.optInt("points", -1), q.optInt("animated", 0) == 1);
                    }
                });
            }
        }).start();
    }

    /** 确认弹窗：显示这张图要消耗多少积分、删除可返还多少 */
    private void confirmUpload(final String path, final int cost, final int refund,
                               final int points, final boolean anim) {
        if (getActivity() == null) return;
        StringBuilder sb = new StringBuilder();
        sb.append("文件：").append(new File(path).getName()).append("\n");
        sb.append("大小：").append(String.format("%.1f", new File(path).length() / 1024.0))
          .append(" KB\n");
        sb.append(anim ? "类型：动图（每 KB 2 积分）\n" : "类型：图片\n");
        sb.append("\n消耗积分：").append(cost).append(" 分\n");
        sb.append("删除可返还：").append(refund).append(" 分（50%）");
        if (points >= 0) sb.append("\n\n当前积分：").append(points).append(" 分");

        new AlertDialog.Builder(getActivity())
                .setTitle("确认上传")
                .setMessage(sb.toString())
                .setNegativeButton("取消", null)
                .setPositiveButton("确认上传", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { uploadAndSend(path); }
                })
                .show();
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

    // ---------- 聊天背景 ----------

    /** 会话标识：频道名 或 与谁私聊 */
    private String bgTarget() {
        return channelCode.isEmpty() ? ("与" + peer + "私聊") : ("频道" + channelCode);
    }

    private void loadChatBg() {
        if (ivChatBg == null || bgTarget().isEmpty()) return;
        if (channelCode.isEmpty() && peer.isEmpty()) return;
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("file.php?action=chat_bg&target="
                        + java.net.URLEncoder.encode(bgTarget()));
                ui(new Runnable() {
                    public void run() {
                        if (ivChatBg == null) return;
                        String u = (r == null) ? "" : r.optString("url", "");
                        if (u.isEmpty()) {
                            ivChatBg.setVisibility(View.GONE);
                            if (chatBgBase != null) chatBgBase.setVisibility(View.GONE);
                            return;
                        }

                        // 版本号：服务器换了新背景 v 就会变，URL 跟着变，
                        // 于是自然绕过图片缓存；没换时 URL 稳定，仍走 24 小时缓存。
                        long v = (r == null) ? 0 : r.optLong("v", 0);
                        String full = Api.absUrl(u);
                        if (v > 0) full += (full.contains("?") ? "&" : "?") + "v=" + v;

                        // 应用锁竖屏，背景统一裁切铺满即可
                        ivChatBg.setScaleType(ImageView.ScaleType.CENTER_CROP);
                        // 底板先铺一层中性色：金色主题的窗口渐变是黄的，
                        // 不隔开会直接透上来把壁纸染黄。
                        if (chatBgBase != null) chatBgBase.setVisibility(View.VISIBLE);
                        ivChatBg.setVisibility(View.VISIBLE);
                        ivChatBg.setTag(full);
                        new ImgLoader(ivChatBg).load(full, 1080, 1920);
                    }
                });
            }
        }).start();
    }

    /** 顶部「设置聊天背景」入口 */
    public void pickChatBg() {
        if (getActivity() == null) return;
        if (channelCode.isEmpty() && peer.isEmpty()) { toast("请先进入频道或私聊"); return; }
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("image/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, REQ_PICK_BG);
    }

    private void uploadChatBg(final String path) {
        final File f = new File(path);
        toast("设置中…");
        new Thread(new Runnable() {
            public void run() {
                HashMap<String, String> ex = new HashMap<String, String>();
                ex.put("target", bgTarget());
                final JSONObject r = Api.upload("file.php?action=set_chat_bg", f, "image", ex);
                ui(new Runnable() {
                    public void run() {
                        if (r == null) { toast("设置失败（可能是服务器拦截了上传）"); return; }
                        if (!r.optBoolean("success", false)) {
                            // 超限等提示走底部消息条，不打断操作
                            toast(r.optString("error", "设置失败"));
                            return;
                        }
                        toast("已设置聊天背景");
                        // 旧背景已由服务端删除，本地缓存里的那份也要丢掉，
                        // 否则同名文件会让界面一直显示上一张。
                        String nu = r.optString("url", "");
                        if (!nu.isEmpty()) ImgLoader.evict(Api.absUrl(nu));
                        loadChatBg();
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
                        // key 是服务端落盘的唯一文件名。两张同名图靠它区分，
                        // 删除必须用它，否则会删错。
                        final List<String> keys = new ArrayList<String>();

                        // 关键：三种形态来自同一份数据，只能取其一。
                        // 之前三个都遍历，同一张图被 collect 多次 —— 这就是「重复显示两张」的根因。
                        boolean got = false;

                        JSONArray gl = r.optJSONArray("groups_list");
                        if (!got && gl != null && gl.length() > 0) {
                            for (int i = 0; i < gl.length(); i++) {
                                JSONObject day = gl.optJSONObject(i);
                                if (day == null) continue;
                                collect(day.optJSONArray("items"), urls, names, keys);
                            }
                            got = !urls.isEmpty();
                        }

                        JSONObject go = r.optJSONObject("groups");
                        if (!got && go != null && go.length() > 0) {
                            java.util.Iterator<String> it = go.keys();
                            while (it.hasNext()) {
                                collect(go.optJSONArray(it.next()), urls, names, keys);
                            }
                            got = !urls.isEmpty();
                        }

                        if (!got) collect(r.optJSONArray("items"), urls, names, keys);
                        if (urls.isEmpty()) { toast("还没有上传记录"); return; }

                        // 带缩略图的网格，点一下直接发送
                        android.widget.ScrollView sv = new android.widget.ScrollView(getActivity());
                        LinearLayout box = new LinearLayout(getActivity());
                        box.setOrientation(LinearLayout.VERTICAL);
                        box.setPadding(12, 8, 12, 8);
                        for (int i = 0; i < urls.size(); i++) {
                            final String u = urls.get(i);
                            final String nm = names.get(i);
                            // i 在循环里会自增，不是 effectively final，
                            // 匿名内部类里直接用会编译失败，这里复制一份 final 的
                            final String ky = (i < keys.size()) ? keys.get(i) : "";
                            LinearLayout row = new LinearLayout(getActivity());
                            row.setOrientation(LinearLayout.HORIZONTAL);
                            row.setGravity(Gravity.CENTER_VERTICAL);
                            row.setPadding(0, 6, 0, 6);

                            android.widget.ImageView thumb =
                                    new android.widget.ImageView(getActivity());
                            thumb.setLayoutParams(new LinearLayout.LayoutParams(120, 120));
                            thumb.setScaleType(android.widget.ImageView.ScaleType.CENTER_CROP);
                            new ImgLoader(thumb).load(Api.absUrl(u), 200, 200);
                            // 点缩略图本身 = 放大查看
                            thumb.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View v) { openImage(u); }
                            });

                            LinearLayout info = new LinearLayout(getActivity());
                            info.setOrientation(LinearLayout.VERTICAL);
                            info.setLayoutParams(new LinearLayout.LayoutParams(
                                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
                            info.setPadding(12, 0, 0, 0);

                            TextView t = new TextView(getActivity());
                            t.setText(nm);
                            t.setTextColor(ThemeEngine.themeText());
                            t.setTextSize(13);
                            t.setSingleLine(true);

                            // 发送 / 删除 两个操作按钮
                            LinearLayout ops = new LinearLayout(getActivity());
                            ops.setOrientation(LinearLayout.HORIZONTAL);
                            ops.setPadding(0, 6, 0, 0);

                            TextView send = new TextView(getActivity());
                            send.setText("发送");
                            send.setTextColor(0xFF4A90D9);
                            send.setTextSize(12);
                            send.setPadding(0, 4, 20, 4);
                            send.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View v) { doSend(u, "image", nm, 0); }
                            });

                            TextView del = new TextView(getActivity());
                            del.setText("删除（返还 50% 积分）");
                            del.setTextColor(0xFFE74C3C);
                            del.setTextSize(12);
                            del.setPadding(0, 4, 0, 4);
                            del.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View v) {
                                    confirmDeleteUpload(nm, ky);
                                }
                            });

                            ops.addView(send);
                            ops.addView(del);
                            info.addView(t);
                            info.addView(ops);

                            row.addView(thumb);
                            row.addView(info);
                            box.addView(row);
                        }
                        sv.addView(box);
                        AlertDialog dlg = new AlertDialog.Builder(getActivity())
                                .setTitle("我的上传")
                                .setView(sv)
                                .setNegativeButton(R.string.btn_close, null)
                                .create();
                        uploadDlg = dlg;
                        try {
                            dlg.setOnDismissListener(new DialogInterface.OnDismissListener() {
                                public void onDismiss(DialogInterface d) { uploadDlg = null; }
                            });
                        } catch (Throwable ignore) {}
                        dlg.show();
                    }
                });
            }
        }).start();
    }

    /** 从 items 数组里收集 url / 显示名 / 唯一 key */
    private void collect(JSONArray items, List<String> urls,
                         List<String> names, List<String> keys) {
        if (items == null) return;
        for (int j = 0; j < items.length(); j++) {
            JSONObject it = items.optJSONObject(j);
            if (it == null) continue;
            String u = it.optString("url", "");
            if (u.isEmpty()) u = it.optString("path", "");
            if (u.isEmpty()) continue;
            // 同一张图可能在多份数据里重复出现，按 url 再拦一道
            if (urls.contains(u)) continue;
            urls.add(u);
            names.add(it.optString("name", "图片"));
            // 老服务端没有 key：用 url 的文件名兜底（同样是唯一的）
            String k = it.optString("key", "");
            if (k.isEmpty()) {
                String p = u;
                int slash = p.lastIndexOf('/');
                k = slash >= 0 ? p.substring(slash + 1) : p;
            }
            keys.add(k);
        }
    }

    /** 删除上传：返还该图片 50% 积分。用 key 精确定位，避免删错同名图 */
    private void confirmDeleteUpload(final String name, final String key) {
        if (getActivity() == null) return;
        new AlertDialog.Builder(getActivity())
                .setTitle("删除这张图片？")
                .setMessage(name + "\n删除后将返还该图片消耗的 50% 积分，且无法恢复。")
                .setNegativeButton("我再想想", null)
                .setPositiveButton("确认删除", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { doDeleteUpload(key, name); }
                })
                .show();
    }

    private void doDeleteUpload(final String key, final String name) {
        toast("删除中…");
        new Thread(new Runnable() {
            public void run() {
                JSONObject b = new JSONObject();
                try {
                    // 优先传 key；服务端没有 key 的老数据会退回按 name 匹配
                    if (key != null && !key.isEmpty()) b.put("key", key);
                    else b.put("name", name);
                } catch (Exception ignore) {}
                final JSONObject r = Api.post("user.php?action=delete_upload", b);
                ui(new Runnable() {
                    public void run() {
                        if (r == null) { toast("删除失败"); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "删除失败"));
                            return;
                        }
                        toast("已删除，返还 " + r.optInt("refund", 0) + " 积分");
                        if (host != null) host.refreshPoints();
                        // 关掉旧的上传弹窗并立刻重开，列表当场更新；
                        // 同时重载聊天，历史里的图片才会变成「图片已被删除」。
                        if (uploadDlg != null) {
                            try { uploadDlg.dismiss(); } catch (Throwable ignore) {}
                            uploadDlg = null;
                        }
                        showUploads();
                        loadHistory();
                    }
                });
            }
        }).start();
    }

    public void openImage(String content) {
        if (getActivity() == null || content == null || content.isEmpty()) return;
        startActivity(new Intent(getActivity(), ImageViewActivity.class)
                .putExtra("url", Api.absUrl(content)));
    }

    // ---------- 消息菜单（撤回 / 复制）----------
    private void showMsgMenu(final Models.Msg m) {
        if (getActivity() == null) return;
        final boolean mine = m.username.equals(Prefs.user());
        final boolean canRecall = mine
                && (System.currentTimeMillis() / 1000 - m.timestamp) < 120;

        final List<String> acts = new ArrayList<String>();
        if (canRecall) acts.add("撤回");
        acts.add("引用");
        acts.add("复制");

        // 菜单贴在长按位置下方（下方没空间就翻上方），不再居中弹窗 + 灰遮罩
        View anchor = (lv != null) ? lv : getActivity().findViewById(android.R.id.content);
        final String target = bgTarget();
        // 消息栏长按不显示免打扰：那是好友 / 频道页的设置项
        MsgMenu.show(getActivity(), anchor, lastTouchX, lastTouchY, acts,
                false, false, new MsgMenu.Callback() {
                    public void onAction(String a) {
                        if ("__mute_on".equals(a)) {
                            Prefs.muted(target, true);
                            toast("已开启免打扰（仍会接收，但不再通知）");
                            return;
                        }
                        if ("__mute_off".equals(a)) {
                            Prefs.muted(target, false);
                            toast("已关闭免打扰");
                            return;
                        }
                        if ("复制".equals(a)) {
                            android.content.ClipboardManager cm =
                                    (android.content.ClipboardManager) getActivity()
                                            .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                            cm.setText(m.content);
                            toast("已复制");
                        } else if ("引用".equals(a)) {
                            setQuote(m);
                        } else {
                            doRecall(m);
                        }
                    }
                });
    }

    /** 设置引用目标，并在输入框上方显示可取消的引用条 */
    private void setQuote(Models.Msg m) {
        quoting = m;
        if (quoteBar == null) return;
        quoteBar.setVisibility(View.VISIBLE);
        if (quoteText != null) {
            String t = "image".equals(m.type) ? "[图片]" : m.content;
            if (t.length() > 30) t = t.substring(0, 30) + "…";
            quoteText.setText("引用 " + m.username + "：" + t);
        }
    }

    private void clearQuote() {
        quoting = null;
        if (quoteBar != null) quoteBar.setVisibility(View.GONE);
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
                        int back = r.optInt("refund", 0);
                        if (back > 0) {
                            toast("已撤回，返还 " + back + " 积分");
                            if (host != null) host.refreshPoints();
                        } else {
                            toast("已撤回");
                        }
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
            // 服务端按发送者回填的个性化样式与身份
            m.uid = o.optString("uid", "");
            m.bubble = o.optString("bubble", "");
            m.namecolor = o.optString("namecolor", "");
            m.replyTo = o.optLong("reply_to", 0);
            out.add(m);
        }
        return out;
    }

    /** 引用后，回填被引用者的昵称与摘要，供气泡上方展示 */
    private void fillQuote(List<Models.Msg> got) {
        if (got == null || got.isEmpty()) return;
        for (Models.Msg m : got) {
            if (m.replyTo <= 0) continue;
            for (Models.Msg src : data) {
                if (src.id > 0 && src.id == m.replyTo) {
                    m.replyName = src.username;
                    m.replyText = "image".equals(src.type) ? "[图片]" : src.content;
                    break;
                }
            }
            if (m.replyName.isEmpty()) m.replyName = "引用消息";
        }
    }

    /**
     * 资料读取回调。
     */
    public interface UserInfoCb {
        void onDone(JSONObject d);
    }

    /**
     * 读取指定用户的资料（uid / 头像 / 简介 / 名称颜色 / 积分 / 在线）。
     */
    public void loadUserInfo(final String who, final UserInfoCb cb) {
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("user.php?action=user_info&username="
                        + java.net.URLEncoder.encode(who == null ? "" : who));
                ui(new Runnable() {
                    public void run() {
                        if (cb == null) return;
                        if (r == null || !r.optBoolean("success", false)) { cb.onDone(null); return; }
                        cb.onDone(r);
                    }
                });
            }
        }).start();
    }

    /**
     * 发送好友申请。
     */
    public void sendFriendRequest(final String who) {
        toast("发送中…");
        new Thread(new Runnable() {
            public void run() {
                JSONObject b = new JSONObject();
                try {
                    b.put("username", who);
                    // 服务端要求申请原因非空
                    b.put("reason", "想加你为好友");
                } catch (Exception ignore) {}
                final JSONObject r = Api.post("user.php?action=send_request", b);
                ui(new Runnable() {
                    public void run() {
                        if (r == null) { toast("发送失败"); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "发送失败"));
                            return;
                        }
                        toast("已发送好友申请");
                    }
                });
            }
        }).start();
    }

    public void toast(String s) {
        if (getActivity() == null) return;
        Toast.makeText(getActivity(), s, Toast.LENGTH_SHORT).show();
    }
}
