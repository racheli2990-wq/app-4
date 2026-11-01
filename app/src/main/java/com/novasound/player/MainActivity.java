package com.novasound.player;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.provider.MediaStore;
import android.view.*;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import java.util.*;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_AUDIO=41, REQ_PICK=42;
    private final int BG=Color.rgb(16,18,16), PANEL=Color.rgb(29,32,28), MUTED=Color.rgb(164,171,157), GREEN=Color.rgb(190,244,111);
    private ExoPlayer player;
    private LinearLayout root, content, mini;
    private TextView nowTitle, nowArtist, playButton;
    private final ArrayList<Track> tracks=new ArrayList<>();
    private final HashSet<String> favorites=new HashSet<>();
    private final HashMap<String,Integer> plays=new HashMap<>();
    private final HashMap<String,Integer> artistLikes=new HashMap<>();
    private final HashSet<String> history=new HashSet<>();
    private String page="home", query="";
    private boolean isPlaying=false;
    private android.content.SharedPreferences prefs;

    static class Track {
        long id; String title, artist, album; Uri uri;
        Track(long i,String t,String a,String b,Uri u){id=i;title=t;artist=a;album=b;uri=u;}
        String key(){return id+":"+title;}
    }

    @Override public void onCreate(Bundle state){
        super.onCreate(state);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG);
        prefs=getSharedPreferences("nova",MODE_PRIVATE);
        favorites.addAll(prefs.getStringSet("favorites",new HashSet<>()));
        plays.putAll(readCounts("plays")); artistLikes.putAll(readCounts("artistLikes"));
        player=new ExoPlayer.Builder(this).build();
        player.addListener(new Player.Listener(){@Override public void onIsPlayingChanged(boolean playing){isPlaying=playing;updateMini();}});
        buildShell();
        if(hasAudioPermission()) loadTracks(); else requestAudioPermission();
    }

    private HashMap<String,Integer> readCounts(String key){
        HashMap<String,Integer> map=new HashMap<>(); Map<String,?> all=prefs.getAll();
        for(String k:all.keySet()) if(k.startsWith(key+"_")) map.put(k.substring(key.length()+1),prefs.getInt(k,0));
        return map;
    }
    private void saveCounts(String key,HashMap<String,Integer> map){
        android.content.SharedPreferences.Editor e=prefs.edit();
        for(String k:prefs.getAll().keySet()) if(k.startsWith(key+"_")) e.remove(k);
        for(String k:map.keySet()) e.putInt(key+"_"+k,map.get(k)); e.apply();
    }
    private boolean hasAudioPermission(){
        if(Build.VERSION.SDK_INT>=33) return ContextCompat.checkSelfPermission(this,Manifest.permission.READ_MEDIA_AUDIO)==PackageManager.PERMISSION_GRANTED;
        return ContextCompat.checkSelfPermission(this,Manifest.permission.READ_EXTERNAL_STORAGE)==PackageManager.PERMISSION_GRANTED;
    }
    private void requestAudioPermission(){
        String p=Build.VERSION.SDK_INT>=33?Manifest.permission.READ_MEDIA_AUDIO:Manifest.permission.READ_EXTERNAL_STORAGE;
        ActivityCompat.requestPermissions(this,new String[]{p},REQ_AUDIO);
    }
    @Override public void onRequestPermissionsResult(int r,String[] p,int[] g){
        super.onRequestPermissionsResult(r,p,g); if(r==REQ_AUDIO && g.length>0 && g[0]==PackageManager.PERMISSION_GRANTED) loadTracks();
        else render();
    }
    private void loadTracks(){
        tracks.clear();
        try{
            Uri collection=MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
            String[] projection={MediaStore.Audio.Media._ID,MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM};
            try(Cursor c=getContentResolver().query(collection,projection,MediaStore.Audio.Media.IS_MUSIC+"!=0",null,MediaStore.Audio.Media.TITLE+" COLLATE NOCASE ASC")){
                if(c!=null) while(c.moveToNext()){
                    long id=c.getLong(0); String title=c.getString(1),artist=c.getString(2),album=c.getString(3);
                    Uri uri=ContentUris.withAppendedId(collection,id);
                    tracks.add(new Track(id,title==null?"Unknown track":title,artist==null?"Unknown artist":artist,album==null?"":album,uri));
                }
            }
        }catch(Exception ignored){}
        render();
    }

    private void buildShell(){
        root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setBackgroundColor(BG);
        setContentView(root);
        LinearLayout header=new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL); header.setPadding(dp(22),dp(18),dp(22),dp(10));
        TextView brand=label("NOVA",26,GREEN,true); TextView sound=label(" SOUND",26,Color.WHITE,true); header.addView(brand);header.addView(sound);
        View spacer=new View(this); header.addView(spacer,new LinearLayout.LayoutParams(0,1,1));
        TextView settings=label("⚙",25,Color.WHITE,false); settings.setPadding(dp(12),dp(4),dp(4),dp(4)); settings.setOnClickListener(v->{page="settings";render();});header.addView(settings);
        root.addView(header);
        ScrollView scroll=new ScrollView(this); content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(dp(20),dp(8),dp(20),dp(24));scroll.addView(content);
        root.addView(scroll,new LinearLayout.LayoutParams(-1,0,1));
        mini=new LinearLayout(this);mini.setOrientation(LinearLayout.VERTICAL);mini.setPadding(dp(16),dp(10),dp(16),dp(12));mini.setBackgroundColor(PANEL);
        root.addView(mini); updateMini();
        LinearLayout nav=new LinearLayout(this);nav.setPadding(dp(8),dp(8),dp(8),dp(8));nav.setGravity(Gravity.CENTER);nav.setBackgroundColor(BG);
        String[][] items={{"⌂","בית","home"},{"◉","חיפוש","search"},{"♫","הספרייה","library"},{"✦","בשבילך","forYou"}};
        for(String[] item:items){LinearLayout b=new LinearLayout(this);b.setGravity(Gravity.CENTER);b.setOrientation(LinearLayout.VERTICAL);TextView ic=label(item[0],20,page.equals(item[2])?GREEN:MUTED,true);TextView tx=label(item[1],11,page.equals(item[2])?GREEN:MUTED,false);b.addView(ic);b.addView(tx);b.setPadding(dp(18),dp(2),dp(18),dp(2));b.setOnClickListener(v->{page=item[2];render();});nav.addView(b,new LinearLayout.LayoutParams(0,-2,1));}
        root.addView(nav);
    }
    private TextView label(String s,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private int dp(float v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private void gap(int h){View v=new View(this);content.addView(v,new LinearLayout.LayoutParams(1,dp(h)));}
    private void heading(String s,String sub){
        content.addView(label(s,27,Color.WHITE,true));
        if(sub!=null){TextView t=label(sub,13,MUTED,false);t.setPadding(0,dp(5),0,0);content.addView(t);}
        gap(18);
    }
    private TextView button(String text,Runnable action,boolean filled){
        TextView b=label(text,14,filled?BG:Color.WHITE,true);b.setGravity(Gravity.CENTER);b.setPadding(dp(17),dp(12),dp(17),dp(12));
        android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(filled?GREEN:PANEL);d.setCornerRadius(dp(24));b.setBackground(d);
        b.setOnClickListener(v->action.run());return b;
    }
    private void render(){
        content.removeAllViews();
        if(page.equals("settings")){renderSettings();return;}
        if(page.equals("search")){renderSearch();return;}
        if(page.equals("library")){renderLibrary();return;}
        if(page.equals("forYou")){renderForYou();return;}
        heading("בוקר טוב.","המוזיקה שלך, בקצב שלך.");
        LinearLayout hero=new LinearLayout(this);hero.setOrientation(LinearLayout.VERTICAL);hero.setPadding(dp(20),dp(22),dp(20),dp(22));
        android.graphics.drawable.GradientDrawable gd=new android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TL_BR,new int[]{0xff34442a,0xff1b241a,0xff20251d});gd.setCornerRadius(dp(22));hero.setBackground(gd);
        hero.addView(label("YOUR SOUND. YOUR WORLD.",11,GREEN,true));TextView big=label("הפלייליסט\nשלך מחכה.",26,Color.WHITE,true);big.setPadding(0,dp(10),0,dp(16));hero.addView(big);
        hero.addView(button("הפעל מוזיקה  ▶",()->playCollection(tracks),true));content.addView(hero);
        gap(26);heading("הושמעו לאחרונה","חזרה למוזיקה שאהבת");
        addTracks(tracks.subList(0,Math.min(5,tracks.size())));
        gap(16);heading("תחנות בשבילך","בחירות מהספרייה המקומית שלך");
        addTracks(recommended().subList(0,Math.min(4,recommended().size())));
        if(tracks.isEmpty()) emptyState();
    }
    private void renderSearch(){
        heading("חיפוש","מצא שיר, אמן או אלבום בספרייה שלך");
        EditText input=new EditText(this);input.setSingleLine(true);input.setTextColor(Color.WHITE);input.setHintTextColor(MUTED);input.setHint("מה בא לך לשמוע?");input.setText(query);input.setPadding(dp(16),dp(12),dp(16),dp(12));input.setBackground(round(PANEL));content.addView(input);
        input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int before,int count){query=s.toString();showSearchResults();}public void afterTextChanged(android.text.Editable e){}});
        gap(20);content.addView(label("תוצאות",18,Color.WHITE,true));gap(10);showSearchResults();
    }
    private void showSearchResults(){
        // Keep results refresh isolated below the search input.
        int start=content.getChildCount()>4?4:content.getChildCount();
        while(content.getChildCount()>start)content.removeViewAt(start);
        String q=query.toLowerCase(Locale.ROOT);ArrayList<Track> found=new ArrayList<>();
        for(Track t:tracks)if((t.title+" "+t.artist+" "+t.album).toLowerCase(Locale.ROOT).contains(q))found.add(t);
        addTracks(found);
    }
    private void renderLibrary(){
        heading("הספרייה שלך","כל המוזיקה במקום אחד");
        LinearLayout row=new LinearLayout(this);row.addView(button("＋ הוסף קבצים",()->pickFiles(),true));content.addView(row);gap(18);
        heading("כל השירים",tracks.size()+" רצועות במכשיר");addTracks(tracks);
        if(tracks.isEmpty())emptyState();
    }
    private void renderForYou(){
        heading("נבחר בשבילך","המלצות שמתעדכנות לפי ההאזנות והאהבות שלך");
        LinearLayout card=new LinearLayout(this);card.setOrientation(LinearLayout.VERTICAL);card.setPadding(dp(18),dp(18),dp(18),dp(18));card.setBackground(round(PANEL));
        card.addView(label("NOVA DISCOVERY",11,GREEN,true));card.addView(label("הטעם שלך מתחדד\nעם כל האזנה.",22,Color.WHITE,true));card.addView(label("המלצות מחושבות מקומית לפי אמנים מועדפים, שירים שאהבת ותדירות השמעה. הנתונים נשארים במכשיר.",13,MUTED,false));content.addView(card);
        gap(22);heading("ההתאמה שלך","מומלץ על סמך הפעילות שלך");ArrayList<Track> rec=recommended();addTracks(rec);
        if(rec.isEmpty())content.addView(label("התחילי להאזין ולסמן שירים שאהבת כדי לבנות המלצות אישיות.",14,MUTED,false));
    }
    private void renderSettings(){
        heading("הגדרות","התאמה אישית של חוויית ההאזנה");
        setting("מראה","ממשק כהה · ירוק ליים · עיצוב מינימליסטי");
        setting("המלצות חכמות","ניתוח מקומי של מועדפים והיסטוריית האזנה");
        setting("פרטיות","העדפות והיסטוריית האזנה נשמרות במכשיר בלבד");
        setting("ספריית מוזיקה","קובצי שמע מהמכשיר או בחירה ידנית");
        gap(20);content.addView(button("סרוק מחדש את הספרייה",()->loadTracks(),true));gap(10);
        content.addView(button("איפוס נתוני המלצות",()->{plays.clear();artistLikes.clear();history.clear();saveCounts("plays",plays);saveCounts("artistLikes",artistLikes);Toast.makeText(this,"נתוני ההמלצות אופסו",Toast.LENGTH_SHORT).show();render();},false));
        gap(10);content.addView(button("ייבוא קובצי שמע",()->pickFiles(),false));
        gap(28);content.addView(label("NOVA SOUND  •  VERSION 1.0",11,MUTED,true));
    }
    private void setting(String title,String desc){LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(16),dp(15),dp(16),dp(15));box.setBackground(round(PANEL));box.addView(label(title,16,Color.WHITE,true));TextView d=label(desc,12,MUTED,false);d.setPadding(0,dp(5),0,0);box.addView(d);content.addView(box);gap(9);}
    private android.graphics.drawable.GradientDrawable round(int color){android.graphics.drawable.GradientDrawable d=new android.graphics.drawable.GradientDrawable();d.setColor(color);d.setCornerRadius(dp(16));return d;}
    private void emptyState(){
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setGravity(Gravity.CENTER);box.setPadding(dp(20),dp(30),dp(20),dp(30));box.setBackground(round(PANEL));
        box.addView(label("♫",34,GREEN,true));box.addView(label("הספרייה שלך עוד ריקה",18,Color.WHITE,true));TextView tx=label("הוסיפי קובצי MP3, M4A, FLAC או WAV מהמכשיר כדי להתחיל.",13,MUTED,false);tx.setGravity(Gravity.CENTER);tx.setPadding(0,dp(8),0,dp(14));box.addView(tx);box.addView(button("בחירת קבצים",()->pickFiles(),true));content.addView(box);
    }
    private void addTracks(List<Track> list){
        for(Track t:list){
            LinearLayout item=new LinearLayout(this);item.setGravity(Gravity.CENTER_VERTICAL);item.setPadding(dp(10),dp(10),dp(8),dp(10));item.setBackground(round(BG));
            TextView cover=label("♫",20,GREEN,true);cover.setGravity(Gravity.CENTER);cover.setBackground(round(PANEL));item.addView(cover,new LinearLayout.LayoutParams(dp(46),dp(46)));
            LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);info.setPadding(dp(12),0,0,0);info.addView(label(t.title,15,Color.WHITE,true));info.addView(label(t.artist+"  ·  "+t.album,11,MUTED,false));item.addView(info,new LinearLayout.LayoutParams(0,-2,1));
            TextView fav=label(favorites.contains(t.key())?"♥":"♡",23,favorites.contains(t.key())?GREEN:MUTED,true);fav.setPadding(dp(8),dp(5),dp(8),dp(5));fav.setOnClickListener(v->{toggleFavorite(t);fav.setText(favorites.contains(t.key())?"♥":"♡");fav.setTextColor(favorites.contains(t.key())?GREEN:MUTED);});item.addView(fav);
            item.setOnClickListener(v->playTrack(t));content.addView(item);gap(4);
        }
    }
    private ArrayList<Track> recommended(){
        ArrayList<Track> out=new ArrayList<>(tracks);
        out.sort((a,b)->{
            int sa=plays.getOrDefault(a.key(),0)*2+(favorites.contains(a.key())?12:0)+artistLikes.getOrDefault(a.artist,0)*4;
            int sb=plays.getOrDefault(b.key(),0)*2+(favorites.contains(b.key())?12:0)+artistLikes.getOrDefault(b.artist,0)*4;
            return Integer.compare(sb,sa);
        });
        return out;
    }
    private void toggleFavorite(Track t){
        if(favorites.contains(t.key()))favorites.remove(t.key());else{favorites.add(t.key());artistLikes.put(t.artist,artistLikes.getOrDefault(t.artist,0)+1);saveCounts("artistLikes",artistLikes);}
        prefs.edit().putStringSet("favorites",new HashSet<>(favorites)).apply();
    }
    private void playTrack(Track t){
        try{player.setMediaItem(MediaItem.fromUri(t.uri));player.prepare();player.play();plays.put(t.key(),plays.getOrDefault(t.key(),0)+1);history.add(t.key());saveCounts("plays",plays);nowTitle.setText(t.title);nowArtist.setText(t.artist);updateMini();}
        catch(Exception e){Toast.makeText(this,"לא ניתן להפעיל את הקובץ הזה",Toast.LENGTH_SHORT).show();}
    }
    private void playCollection(List<Track> list){
        if(list.isEmpty()){pickFiles();return;}
        ArrayList<MediaItem> items=new ArrayList<>();for(Track t:list)items.add(MediaItem.fromUri(t.uri));
        player.setMediaItems(items);player.prepare();player.play();Track t=list.get(0);nowTitle.setText(t.title);nowArtist.setText(t.artist);updateMini();
    }
    private void updateMini(){
        if(mini==null)return;mini.removeAllViews();
        LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout info=new LinearLayout(this);info.setOrientation(LinearLayout.VERTICAL);nowTitle=label(player!=null&&player.getMediaMetadata().title!=null?player.getMediaMetadata().title.toString():"בחרי רצועה להפעלה",14,Color.WHITE,true);
        nowArtist=label("NOVA SOUND",11,MUTED,false);info.addView(nowTitle);info.addView(nowArtist);row.addView(info,new LinearLayout.LayoutParams(0,-2,1));
        playButton=label(isPlaying?"Ⅱ":"▶",23,GREEN,true);playButton.setPadding(dp(15),dp(4),dp(8),dp(4));playButton.setOnClickListener(v->{if(player.getMediaItemCount()==0)return;if(player.isPlaying())player.pause();else player.play();});row.addView(playButton);
        mini.addView(row);
        SeekBar seek=new SeekBar(this);seek.setMax(1000);if(player!=null&&player.getDuration()>0)seek.setProgress((int)(1000f*player.getCurrentPosition()/player.getDuration()));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean u){if(u&&player.getDuration()>0)player.seekTo(player.getDuration()*p/1000);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        mini.addView(seek,new LinearLayout.LayoutParams(-1,dp(18)));
    }
    private void pickFiles(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("audio/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE,true);startActivityForResult(i,REQ_PICK);
    }
    @Override protected void onActivityResult(int r,int c,Intent data){
        super.onActivityResult(r,c,data);if(r==REQ_PICK&&c==RESULT_OK&&data!=null){
            ArrayList<Uri> uris=new ArrayList<>();if(data.getClipData()!=null){for(int n=0;n<data.getClipData().getItemCount();n++)uris.add(data.getClipData().getItemAt(n).getUri());}else if(data.getData()!=null)uris.add(data.getData());
            ArrayList<MediaItem> items=new ArrayList<>();for(Uri u:uris){try{getContentResolver().takePersistableUriPermission(u,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}items.add(MediaItem.fromUri(u));}
            if(!items.isEmpty()){player.setMediaItems(items);player.prepare();player.play();nowTitle.setText("רשימת השמעה חדשה");nowArtist.setText(items.size()+" קבצים נבחרו");updateMini();}
        }
    }
    @Override protected void onDestroy(){super.onDestroy();if(player!=null)player.release();}
}
