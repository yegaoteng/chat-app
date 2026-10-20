package xfan.chat.app;

import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 下载页：进行中的任务 + 历史记录，支持暂停 / 继续 / 取消 / 删除。 */
public class DownloadFragment extends android.app.Fragment {

    private View rootView;
    private ListView lv;
    private TextView tvEmpty;
    private Ad ad;
    private final Handler h = new Handler();
    private final List<Downloads.Task> list = new ArrayList<Downloads.Task>();

    /** 刷新列表 + 进度（进行中的任务每秒刷新一次） */
    private final Runnable tick = new Runnable() {
        public void run() {
            refresh();
            h.postDelayed(this, 1000);
        }
    };

    @Override
    public View onCreateView(LayoutInflater inf, ViewGroup c, Bundle b) {
        rootView = inf.inflate(R.layout.frag_download, c, false);
        lv = rootView.findViewById(R.id.lvDownload);
        tvEmpty = rootView.findViewById(R.id.tvEmpty);

        ad = new Ad();
        lv.setAdapter(ad);

        rootView.findViewById(R.id.btnResumeAll)
                .setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Downloads.resumeAll(getActivity());
                        refresh();
                    }
                });
        rootView.findViewById(R.id.btnPauseAll)
                .setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        Downloads.pauseAll();
                        refresh();
                    }
                });

        Downloads.setListener(new Downloads.Listener() {
            public void onChanged() { refresh(); }
        });
        refresh();
        return rootView;
    }

    /** 切到本页时刷新一次 */
    public void onShow() { refresh(); }

    @Override
    public void onResume() {
        super.onResume();
        h.postDelayed(tick, 1000);
    }

    @Override
    public void onPause() {
        h.removeCallbacks(tick);
        super.onPause();
    }

    private void refresh() {
        if (lv == null || ad == null) return;
        list.clear();
        list.addAll(Downloads.all());
        ad.notifyDataSetChanged();
        if (tvEmpty != null) {
            tvEmpty.setVisibility(list.isEmpty() ? View.VISIBLE : View.GONE);
        }
    }

    private class Ad extends BaseAdapter {
        public int getCount() { return list.size(); }

        public Object getItem(int p) { return list.get(p); }

        public long getItemId(int p) { return p; }

        public View getView(final int pos, View cv, ViewGroup parent) {
            final Downloads.Task t = list.get(pos);
            View v = cv;
            if (v == null) {
                v = LayoutInflater.from(getActivity())
                        .inflate(R.layout.item_download, parent, false);
            }
            TextView name = v.findViewById(R.id.tvName);
            TextView info = v.findViewById(R.id.tvInfo);
            ProgressBar pb = v.findViewById(R.id.pb);
            TextView btnT = v.findViewById(R.id.btnToggle);
            TextView btnR = v.findViewById(R.id.btnRemove);

            name.setText(t.name);
            pb.setProgress(t.percent());

            // 状态文案
            String st;
            switch (t.state) {
                case Downloads.ST_DONE:
                    st = "已完成 · " + Downloads.sizeText(t.done);
                    break;
                case Downloads.ST_PAUSED:
                    st = "已暂停 · " + Downloads.sizeText(t.done);
                    break;
                case Downloads.ST_ERROR:
                    st = "失败：" + t.error;
                    break;
                case Downloads.ST_CANCEL:
                    st = "已取消";
                    break;
                default:
                    st = Downloads.sizeText(t.done) + " / "
                            + Downloads.sizeText(t.total);
                    if (t.speed > 0) st += " · " + Downloads.speedText(t.speed);
                    if (t.resume) st += " · 续传";
                    break;
            }
            // 用户在系统里自己删了文件，历史记录就显示「文件不存在」
            if (t.state == Downloads.ST_DONE && !t.fileExists()) {
                st += " · 文件不存在";
            }
            info.setText(st);

            // 已完成的任务不能再暂停，按钮变成「删除」
            if (t.state == Downloads.ST_DONE || t.state == Downloads.ST_ERROR
                    || t.state == Downloads.ST_CANCEL) {
                btnT.setVisibility(View.GONE);
            } else {
                btnT.setVisibility(View.VISIBLE);
                btnT.setText(t.state == Downloads.ST_RUNNING ? "暂停" : "继续");
            }
            btnT.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    if (t.state == Downloads.ST_RUNNING) Downloads.pause(t);
                    else Downloads.resume(getActivity(), t);
                    refresh();
                }
            });

            btnR.setText(t.finished() ? "删除" : "取消");
            btnR.setOnClickListener(new View.OnClickListener() {
                public void onClick(View vv) {
                    if (!t.finished()) {
                        // 进行中/暂停：「取消」直接删掉半截文件，不再二次确认
                        Downloads.cancel(getActivity(), t);
                        refresh();
                        return;
                    }
                    confirmRemove(t);
                }
            });
            return v;
        }
    }

    /** 删除历史记录：再确认一次，并可勾选「同时删除已下载的文件」 */
    private void confirmRemove(final Downloads.Task t) {
        if (getActivity() == null) return;
        final android.widget.CheckBox cb = new android.widget.CheckBox(getActivity());
        cb.setText("同时删除已下载的文件");
        cb.setChecked(false);
        // 文件已经不在了就不用给这个选项
        cb.setVisibility(t.fileExists() ? View.VISIBLE : View.GONE);

        android.widget.LinearLayout box = new android.widget.LinearLayout(getActivity());
        box.setOrientation(android.widget.LinearLayout.VERTICAL);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        box.setPadding(p, p / 2, p, 0);
        box.addView(cb);

        new android.app.AlertDialog.Builder(getActivity(), R.style.XfanDialog)
                .setTitle("删除这条记录？")
                .setView(box)
                .setNegativeButton("我再想想", null)
                .setPositiveButton("确认删除",
                        new android.content.DialogInterface.OnClickListener() {
                            public void onClick(android.content.DialogInterface d, int w) {
                                Downloads.remove(getActivity(), t, cb.isChecked());
                                refresh();
                            }
                        })
                .show();
    }
}
