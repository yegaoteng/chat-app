package xfan.chat.app;

import android.app.AlertDialog;
import android.app.Fragment;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.InputType;
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

import java.util.ArrayList;
import java.util.List;

public class FriendsFragment extends Fragment {

    public MainActivity host;
    private ListView lvChannel, lvFriend;
    private final List<Models.Channel> chans = new ArrayList<Models.Channel>();
    private final List<Models.Friend> friends = new ArrayList<Models.Friend>();
    private BaseAdapter chAd, frAd;

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle b) {
        View v = inf.inflate(R.layout.frag_friends, c, false);
        lvChannel = v.findViewById(R.id.lvChannel);
        lvFriend = v.findViewById(R.id.lvFriend);
        Button btnJoin = v.findViewById(R.id.btnJoin);
        Button btnAdd = v.findViewById(R.id.btnAdd);
        Button btnReq = v.findViewById(R.id.btnReq);

        chAd = new BaseAdapter() {
            public int getCount() { return chans.size(); }
            public Object getItem(int p) { return chans.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p, View cv, ViewGroup parent) {
                if (cv == null || !(cv.findViewById(R.id.tvIcon) instanceof TextView)) {
                    cv = LayoutInflater.from(getActivity())
                            .inflate(R.layout.item_channel, parent, false);
                }
                Models.Channel ch = chans.get(p);
                TextView icon = cv.findViewById(R.id.tvIcon);
                TextView name = cv.findViewById(R.id.tvName);
                TextView unread = cv.findViewById(R.id.tvUnread);
                icon.setText(ch.pinned ? "★" : "#");
                name.setText((ch.pinned ? "「置顶」" : "") + ch.name);
                name.setTextColor(ThemeEngine.themeText());
                if (ch.unread > 0) {
                    unread.setVisibility(View.VISIBLE);
                    unread.setText(String.valueOf(ch.unread));
                } else {
                    unread.setVisibility(View.GONE);
                }
                return cv;
            }
        };
        lvChannel.setAdapter(chAd);
        lvChannel.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v2, int pos, long id) {
                Models.Channel ch = chans.get(pos);
                if (host != null && host.fChat != null) host.fChat.openChannel(ch.code, ch.name);
            }
        });

        // 长按频道：置顶 / 退出频道
        lvChannel.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v2, int pos, long id) {
                showChannelMenu(chans.get(pos));
                return true;
            }
        });

        frAd = new BaseAdapter() {
            public int getCount() { return friends.size(); }
            public Object getItem(int p) { return friends.get(p); }
            public long getItemId(int p) { return p; }
            public View getView(int p, View cv, ViewGroup parent) {
                if (cv == null) cv = LayoutInflater.from(getActivity())
                        .inflate(R.layout.item_friend, parent, false);
                Models.Friend f = friends.get(p);
                ImageView av = cv.findViewById(R.id.ivAv);
                TextView name = cv.findViewById(R.id.tvName);
                TextView last = cv.findViewById(R.id.tvLast);
                TextView pin = cv.findViewById(R.id.tvPin);
                View dot = cv.findViewById(R.id.vUnread);

                name.setText(f.username);
                if (f.online) {
                    // 「在线」用绿色跟在昵称后面
                    android.text.SpannableString sp =
                            new android.text.SpannableString(f.username + "  在线");
                    sp.setSpan(new android.text.style.ForegroundColorSpan(0xFF2E7D32),
                            f.username.length(), sp.length(),
                            android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    name.setText(sp);
                }
                last.setText(f.last.isEmpty() ? "还没有聊天" : f.last);
                pin.setVisibility(f.pinned ? View.VISIBLE : View.GONE);
                dot.setVisibility(f.unread > 0 ? View.VISIBLE : View.GONE);

                String url = f.avatar == null ? "" : f.avatar;
                if (!url.isEmpty()) {
                    new ImgLoader(av).load(Api.absUrl(url), 96, 96);
                } else {
                    av.setImageDrawable(new Avatars(f.username));
                }
                // 关键：用好友自己的名称颜色，不能用「我」的。
                // 之前传 null 会套用我的个性化，导致：
                //   1) 别人看我时显示成他自己的颜色
                //   2) 我看别人时全员显示成我的颜色
                ThemeEngine.applyNameColorFor(name, f.namecolor);
                last.setTextColor(ThemeEngine.themeTextSub());
                return cv;
            }
        };
        lvFriend.setAdapter(frAd);

        lvFriend.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            public void onItemClick(AdapterView<?> p, View v2, int pos, long id) {
                Models.Friend f = friends.get(pos);
                if (host != null && host.fChat != null) host.fChat.openPm(f.username);
            }
        });

        lvFriend.setOnItemLongClickListener(new AdapterView.OnItemLongClickListener() {
            public boolean onItemLongClick(AdapterView<?> p, View v2, int pos, long id) {
                showFriendMenu(friends.get(pos));
                return true;
            }
        });

        btnJoin.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) { joinChannel(); }
        });
        btnAdd.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) { addFriend(); }
        });
        btnReq.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v2) { showRequests(); }
        });
        return v;
    }

    /** 频道长按菜单：置顶频道 / 退出频道 */
    private void showChannelMenu(final Models.Channel ch) {
        if (getActivity() == null) return;
        final List<String> acts = new ArrayList<String>();
        acts.add(ch.pinned ? "取消置顶" : "置顶频道");
        acts.add("退出频道");

        new AlertDialog.Builder(getActivity())
                .setTitle(ch.name)
                .setItems(acts.toArray(new String[0]), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        if (w == 0) doPinChannel(ch);
                        else confirmQuitChannel(ch);
                    }
                }).show();
    }

    private void doPinChannel(final Models.Channel ch) {
        new Thread(new Runnable() {
            public void run() {
                JSONObject b = new JSONObject();
                try { b.put("code", ch.code); } catch (Exception ignore) {}
                final JSONObject r = Api.post("user.php?action=pin_channel", b);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) { toast("网络错误"); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "操作失败"));
                            return;
                        }
                        toast(r.optBoolean("pinned", false) ? "已置顶" : "已取消置顶");
                        loadChannels();
                    }
                });
            }
        }).start();
    }

    private void confirmQuitChannel(final Models.Channel ch) {
        if (getActivity() == null) return;
        new AlertDialog.Builder(getActivity())
                .setTitle("退出频道")
                .setMessage("确定退出「" + ch.name + "」吗？退出后需要重新输入频道码才能加入。")
                .setNegativeButton("我再想想", null)
                .setPositiveButton("退出", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { doQuitChannel(ch); }
                })
                .show();
    }

    private void doQuitChannel(final Models.Channel ch) {
        new Thread(new Runnable() {
            public void run() {
                JSONObject b = new JSONObject();
                try { b.put("code", ch.code); } catch (Exception ignore) {}
                final JSONObject r = Api.post("user.php?action=quit_channel", b);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) { toast("网络错误"); return; }
                        if (!r.optBoolean("success", false)) {
                            toast(r.optString("error", "退出失败"));
                            return;
                        }
                        toast("已退出频道");
                        loadChannels();
                    }
                });
            }
        }).start();
    }

    /** 应用个性化主题 */
    public void applyTheme() {
        View v = getView();
        if (v == null) return;
        try {
            View cb = v.findViewById(R.id.chanBar);
            View fb = v.findViewById(R.id.friendBar);
            if (cb != null) cb.setBackgroundColor(ThemeEngine.themePanel());
            if (fb != null) fb.setBackgroundColor(ThemeEngine.themePanel());
        } catch (Exception ignore) {}
        if (chAd != null) chAd.notifyDataSetChanged();
        if (frAd != null) frAd.notifyDataSetChanged();
    }

    /** 资料回来后刷新（昵称颜色等） */
    public void applyProfile(JSONObject r) {
        // 视图可能尚未创建（数据在 onCreateView 之前就回来了）
        if (getActivity() == null || getView() == null) return;
        applyTheme();
    }

    public void onShow() { load(); }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) load();
    }

    // ---------- 数据 ----------
    private void load() {
        loadChannels();
        loadFriends();
    }

    /** 失败时说明原因，避免界面停在“加载中”却毫无反馈 */
    private void why(JSONObject r) {
        String msg = (r == null)
                ? ("网络错误\n" + Api.lastUrl + "\n" + Api.lastError)
                : r.optString("error", "加载失败");
        if (getActivity() != null)
            android.widget.Toast.makeText(getActivity(), msg,
                    android.widget.Toast.LENGTH_LONG).show();
    }

    private void loadChannels() {
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("user.php?action=my_channels");
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null || !r.optBoolean("success", false)) { why(r); return; }
                        JSONArray arr = r.optJSONArray("channels");
                        if (arr == null) return;
                        chans.clear();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.optJSONObject(i);
                            if (o == null) continue;
                            Models.Channel c = new Models.Channel();
                            c.code = o.optString("code", "");
                            c.name = o.optString("name", c.code);
                            c.lastTs = o.optLong("last_ts", 0);
                            c.pinned = o.optBoolean("pinned", false);
                            chans.add(c);
                        }
                        chAd.notifyDataSetChanged();
                        setLvHeight(lvChannel, chans.size());
                    }
                });
            }
        }).start();
    }

    private void loadFriends() {
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("user.php?action=friends");
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null || !r.optBoolean("success", false)) { why(r); return; }
                        JSONArray arr = r.optJSONArray("friends");
                        if (arr == null) return;
                        friends.clear();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.optJSONObject(i);
                            if (o == null) continue;
                            Models.Friend f = new Models.Friend();
                            f.username = o.optString("username", "");
                            f.avatar = o.optString("avatar", "");
                            f.namecolor = o.optString("namecolor", "default");
                            f.online = o.optBoolean("online", false);
                            f.last = o.optString("last", "");
                            f.lastTs = o.optLong("last_ts", 0);
                            f.unread = o.optInt("unread", 0);
                            f.pinned = o.optBoolean("pinned", false);
                            friends.add(f);
                        }
                        frAd.notifyDataSetChanged();
                    }
                });
            }
        }).start();
    }

    private void setLvHeight(ListView lv, int n) {
        if (n <= 0) return;
        int rows = Math.min(n, 5);
        int h = (int) (56 * rows * getResources().getDisplayMetrics().density);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) lv.getLayoutParams();
        lp.height = h;
        lv.setLayoutParams(lp);
    }

    // ---------- 好友菜单：置顶 / 删除 ----------
    private void showFriendMenu(final Models.Friend f) {
        String[] acts = f.pinned ? new String[]{"取消置顶", "删除好友"}
                                 : new String[]{"置顶", "删除好友"};
        new AlertDialog.Builder(getActivity())
                .setTitle(f.username)
                .setItems(acts, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        if (w == 0) doPin(f, !f.pinned);
                        else confirmDelete(f);
                    }
                }).show();
    }

    private void confirmDelete(final Models.Friend f) {
        AlertDialog dlg = new AlertDialog.Builder(getActivity())
                .setTitle(R.string.fr_del_title)
                .setMessage(R.string.fr_del_body)
                .setNegativeButton(R.string.fr_del_think, null)
                .setPositiveButton(R.string.fr_del_confirm, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { doDelete(f); }
                })
                .create();
        dlg.setOnShowListener(new DialogInterface.OnShowListener() {
            public void onShow(DialogInterface d) {
                Button btn = ((AlertDialog) d).getButton(AlertDialog.BUTTON_POSITIVE);
                if (btn != null) btn.setTextColor(getResources().getColor(R.color.danger));
            }
        });
        dlg.show();
    }

    private void doDelete(final Models.Friend f) {
        new Thread(new Runnable() {
            public void run() {
                JSONObject b = new JSONObject();
                try { b.put("username", f.username); } catch (Exception ignore) {}
                final JSONObject r = Api.post("user.php?action=friend_remove", b);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        toast(r != null && r.optBoolean("success", false) ? "已删除" : "删除失败");
                        loadFriends();
                    }
                });
            }
        }).start();
    }

    private void doPin(final Models.Friend f, final boolean pin) {
        new Thread(new Runnable() {
            public void run() {
                JSONObject b = new JSONObject();
                try { b.put("username", f.username); b.put("pin", pin); } catch (Exception ignore) {}
                final JSONObject r = Api.post("user.php?action=friend_pin", b);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() { loadFriends(); }
                });
            }
        }).start();
    }

    // ---------- 添加好友 ----------
    private void addFriend() {
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("user.php?action=addable_users");
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null || !r.optBoolean("success", false)) { toast("加载失败"); return; }
                        JSONArray arr = r.optJSONArray("users");
                        if (arr == null || arr.length() == 0) { toast("没有可添加的用户"); return; }
                        final List<String> names = new ArrayList<String>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.optJSONObject(i);
                            if (o == null) continue;
                            String st = o.optString("state", "");
                            if ("friend".equals(st)) continue;
                            names.add(o.optString("username", ""));
                        }
                        if (names.isEmpty()) { toast("没有可添加的用户"); return; }
                        new AlertDialog.Builder(getActivity())
                                .setTitle("选择用户")
                                .setItems(names.toArray(new String[0]),
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface d, int w) {
                                                askReason(names.get(w));
                                            }
                                        }).show();
                    }
                });
            }
        }).start();
    }

    private void askReason(final String who) {
        final EditText ed = new EditText(getActivity());
        ed.setHint(R.string.fr_reason_hint);
        ed.setSingleLine(false);
        new AlertDialog.Builder(getActivity())
                .setTitle("申请添加 " + who)
                .setView(ed)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton("发送申请", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        final String reason = ed.getText().toString().trim();
                        if (reason.isEmpty()) { toast("请填写申请原因"); return; }
                        new Thread(new Runnable() {
                            public void run() {
                                JSONObject b = new JSONObject();
                                try {
                                    b.put("username", who);
                                    b.put("reason", reason);
                                } catch (Exception ignore) {}
                                final JSONObject r = Api.post("user.php?action=send_request", b);
                                if (getActivity() == null) return;
                                getActivity().runOnUiThread(new Runnable() {
                                    public void run() {
                                        toast(r != null && r.optBoolean("success", false)
                                                ? "申请已发送"
                                                : (r == null ? "网络错误" : r.optString("error", "失败")));
                                    }
                                });
                            }
                        }).start();
                    }
                }).show();
    }

    // ---------- 好友申请 ----------
    private void showRequests() {
        new Thread(new Runnable() {
            public void run() {
                final JSONObject r = Api.get("user.php?action=requests");
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        if (r == null) { toast("网络错误"); return; }
                        JSONArray arr = r.optJSONArray("incoming");
                        if (arr == null || arr.length() == 0) { toast("暂无好友申请"); return; }
                        final List<Models.Req> list = new ArrayList<Models.Req>();
                        List<String> labels = new ArrayList<String>();
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.optJSONObject(i);
                            if (o == null) continue;
                            Models.Req q = new Models.Req();
                            q.id = o.optInt("id", 0);
                            q.username = o.optString("username", "");
                            q.avatar = o.optString("avatar", "");
                            q.reason = o.optString("reason", "");
                            list.add(q);
                            labels.add(q.username + "：" + q.reason);
                        }
                        new AlertDialog.Builder(getActivity())
                                .setTitle("好友申请")
                                .setItems(labels.toArray(new String[0]),
                                        new DialogInterface.OnClickListener() {
                                            public void onClick(DialogInterface d, int w) {
                                                handleReq(list.get(w));
                                            }
                                        }).show();
                    }
                });
            }
        }).start();
    }

    private void handleReq(final Models.Req q) {
        new AlertDialog.Builder(getActivity())
                .setTitle(q.username)
                .setMessage(q.reason.isEmpty() ? "想添加你为好友" : q.reason)
                .setNegativeButton("拒绝", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { respond(q.id, false); }
                })
                .setPositiveButton("同意", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) { respond(q.id, true); }
                }).show();
    }

    private void respond(final int id, final boolean accept) {
        new Thread(new Runnable() {
            public void run() {
                JSONObject b = new JSONObject();
                try { b.put("id", id); b.put("accept", accept); } catch (Exception ignore) {}
                final JSONObject r = Api.post("user.php?action=handle_request", b);
                if (getActivity() == null) return;
                getActivity().runOnUiThread(new Runnable() {
                    public void run() {
                        toast(r != null && r.optBoolean("success", false)
                                ? (accept ? "已同意" : "已拒绝")
                                : (r == null ? "网络错误" : r.optString("error", "失败")));
                        loadFriends();
                    }
                });
            }
        }).start();
    }

    // ---------- 加入频道 ----------
    private void joinChannel() {
        final EditText ed = new EditText(getActivity());
        ed.setHint(R.string.ch_code_hint);
        ed.setInputType(InputType.TYPE_CLASS_NUMBER);
        new AlertDialog.Builder(getActivity())
                .setTitle(R.string.ch_join)
                .setView(ed)
                .setNegativeButton(R.string.btn_cancel, null)
                .setPositiveButton("加入", new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        final String code = ed.getText().toString().trim();
                        if (code.length() != 8) { toast("频道码为 8 位"); return; }
                        new Thread(new Runnable() {
                            public void run() {
                                JSONObject b = new JSONObject();
                                try { b.put("code", code); } catch (Exception ignore) {}
                                final JSONObject r = Api.post("user.php?action=join_channel", b);
                                if (getActivity() == null) return;
                                getActivity().runOnUiThread(new Runnable() {
                                    public void run() {
                                        toast(r != null && r.optBoolean("success", false)
                                                ? "已加入" : (r == null ? "网络错误" : r.optString("error", "失败")));
                                        loadChannels();
                                    }
                                });
                            }
                        }).start();
                    }
                }).show();
    }

    private void toast(final String s) {
        if (getActivity() == null) return;
        getActivity().runOnUiThread(new Runnable() {
            public void run() { Toast.makeText(getActivity(), s, Toast.LENGTH_SHORT).show(); }
        });
    }
}
