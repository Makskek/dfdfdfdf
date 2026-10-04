package com.courieranalytics.nativeapp

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.*
import android.widget.*
import java.io.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.*

private const val PREF = "courier_analytics"
private const val DATA = "data"

class MainActivity : Activity() {
    private lateinit var store: Store
    private lateinit var root: LinearLayout
    private lateinit var content: LinearLayout
    private var tab = "dashboard"
    private var theme = "dark"
    private var shiftStarted = 0L
    private var breakStarted = 0L
    private var shiftBreak = 0L
    private var income = 0.0
    private var orders = 0
    private var distance = 0.0
    private var currentLocation: Location? = null
    private val route = mutableListOf<Location>()
    private var locationManager: LocationManager? = null
    private var recorder: MediaRecorder? = null
    private var recordingFile: File? = null

    override fun onCreate(b: Bundle?) { super.onCreate(b); store = Store(this); theme = store.get("theme", "dark"); buildShell(); render() }

    private fun buildShell() {
        root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg()) }
        val bar = LinearLayout(this).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(18, 12, 12, 12); setBackgroundColor(surface()) }
        val menu = button("☰", 42) { openDrawer() }; bar.addView(menu)
        val title = TextView(this).apply { text = "Courier Analytics Pro"; textSize = 19f; setTextColor(fg()); typeface = Typeface.DEFAULT_BOLD; setPadding(12,0,0,0) }
        bar.addView(title, LinearLayout.LayoutParams(0, 56, 1f))
        val clock = TextView(this).apply { text = now(); textSize = 12f; setTextColor(sec()) }; bar.addView(clock, LinearLayout.LayoutParams(-2,56))
        root.addView(bar)
        content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(14, 10, 14, 20) }
        root.addView(ScrollView(this).apply { addView(content) }, LinearLayout.LayoutParams(-1,0,1f))
        setContentView(root)
        Handler(mainLooper).post(object: Runnable { override fun run(){ clock.text=now(); Handler(mainLooper).postDelayed(this,1000) }})
    }

    private fun render(){ content.removeAllViews(); when(tab){
        "dashboard" -> dashboard(); "slots" -> slots(); "expenses" -> expenses(); "goals" -> goals(); "transport" -> transport(); "settings" -> settings()
    } }

    private fun dashboard(){
        h("Dashboard"); p("Courier Analytics Pro — native Kotlin edition")
        val period = row(); listOf("Сегодня","Неделя","Месяц").forEach { b -> period.addView(button(b,0){ toast("Период: $b"); render() }) }; content.addView(period)
        card("Активная смена") {
            val timer=TextView(this).apply{textSize=32f;setTextColor(accent());typeface=Typeface.DEFAULT_BOLD}
            addView(timer)
            val actions=row(); actions.addView(button(if(shiftStarted==0L)"▶ Начать смену" else "■ Завершить",0){ if(shiftStarted==0L) startShift() else finishShift(); render() })
            actions.addView(button(if(breakStarted==0L)"Ⅱ Перерыв" else "▶ Продолжить",0){ toggleBreak(); render() }); addView(actions)
            Handler(mainLooper).post(object:Runnable{override fun run(){if(isFinishing)return; timer.text=if(shiftStarted==0L)"Смена не начата" else duration(shiftElapsed()); Handler(mainLooper).postDelayed(this,1000)}})
        }
        statsGrid()
        card("Быстрый заказ") { val name=input("Ресторан / площадка"); val sum=input("Сумма ₽","number"); addView(name);addView(sum);addView(button("＋ Добавить заказ",0){income+=sum.text.toString().toDoubleOrNull()?:0.0;orders++;storeData();toast("Заказ добавлен");render()}) }
        card("GPS") { val s=TextView(this).apply{text="Точек: ${route.size} • Дистанция: ${fmt(distance)} км";setTextColor(sec())};addView(s);addView(button("◎ Включить GPS",0){startGps()});addView(button("Экспорт GPX",0){exportGpx()}) }
        card("Аналитика") { addView(ChartView(this)); addView(button("Показать детали",0){tab="slots";render()}) }
    }

    private fun statsGrid(){ val g=GridLayout(this).apply{columnCount=2}; val vals=listOf("Доход" to money(income),"Заказы" to orders.toString(),"Пробег" to "${fmt(distance)} км","Средний заказ" to money(if(orders>0)income/orders else 0.0),"Чистыми" to money(net()),"₽/ч" to money(hourRate())); vals.forEach{(a,b)->val t=TextView(this).apply{text="$a\n$b";textSize=17f;setTextColor(fg());setPadding(16,18,16,18);setBackgroundColor(surface())};g.addView(t,GridLayout.LayoutParams().apply{width=0;height=120;columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);setMargins(5,5,5,5)})};content.addView(g)}

    private fun slots(){ h("Слоты смен"); addViewRow(button("＋ Добавить слот",0){slotDialog(null)}); val raw=store.get("slots",""); if(raw.isBlank()) p("Слотов пока нет. Добавьте рабочий слот.") else raw.split("|").filter{it.isNotBlank()}.forEachIndexed{i,s->val f=s.split(";");card("${f.getOrNull(0)?:(i+1).toString()} • ${f.getOrNull(1)?:("")}"){p("Заказы: ${f.getOrNull(2)?:("0")} • Доход: ${f.getOrNull(3)?:("0")}");addViewRow(button("Изменить",0){slotDialog(i)});addViewRow(button("Удалить",0){deleteSlot(i)})}}; addViewRow(button("Экспорт CSV",0){exportSlotsCsv()}); addViewRow(button("Экспорт JSON",0){exportJson()}) }

    private fun slotDialog(index:Int?){ val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL}; val d=input("Дата", "date");val type=input("Платформа");val ord=input("Заказы","number");val inc=input("Доход ₽","number");listOf(d,type,ord,inc).forEach{box.addView(it)}; AlertDialog.Builder(this).setTitle(if(index==null)"Новый слот" else "Редактировать слот").setView(box).setPositiveButton("Сохранить"){_,_->val old=store.get("slots","").split("|").filter{it.isNotBlank()}.toMutableList();val s="${d.text};${type.text};${ord.text};${inc.text}";if(index==null)old.add(s)else old[index!!]=s;store.put("slots",old.joinToString("|"));render()}.setNegativeButton("Отмена",null).show() }
    private fun deleteSlot(i:Int){val a=store.get("slots","").split("|").filter{it.isNotBlank()}.toMutableList();if(i<a.size){a.removeAt(i);store.put("slots",a.joinToString("|"));render()}}

    private fun expenses(){ h("Расходы"); val list=store.get("expenses","").split("|").filter{it.isNotBlank()}; var total=0.0; list.forEach{total+=it.split(";").getOrNull(2)?.toDoubleOrNull()?:0.0}; card("Итого") { val t=TextView(this).apply{text=money(total);textSize=30f;setTextColor(red())};addView(t)}; addViewRow(button("＋ Добавить расход",0){expenseDialog()}); list.forEachIndexed{i,e->val f=e.split(";");card("${f.getOrNull(0)} • ${f.getOrNull(1)}"){p(money(f.getOrNull(2)?.toDoubleOrNull()?:0.0));addView(button("Удалить",0){val a=list.toMutableList();a.removeAt(i);store.put("expenses",a.joinToString("|"));render()})}} }
    private fun expenseDialog(){val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};val date=input("Дата","date");val cat=input("Категория");val amount=input("Сумма ₽","number");val note=input("Комментарий");listOf(date,cat,amount,note).forEach{b.addView(it)};AlertDialog.Builder(this).setTitle("Расход").setView(b).setPositiveButton("Сохранить"){_,_->val a=store.get("expenses","").split("|").filter{it.isNotBlank()}.toMutableList();a.add("${date.text};${cat.text};${amount.text};${note.text}");store.put("expenses",a.joinToString("|"));render()}.setNegativeButton("Отмена",null).show()}

    private fun goals(){h("Цели и бонусы");addViewRow(button("＋ Создать цель",0){goalDialog()}); val goals=store.get("goals","").split("|").filter{it.isNotBlank()};if(goals.isEmpty())p("Целей нет") else goals.forEach{g->val f=g.split(";");card(f.getOrNull(0)?:("Цель")){p("Цель: ${f.getOrNull(1)} • Прогресс: ${f.getOrNull(2)}")}};addViewRow(button("＋ Бонус за заказы",0){bonusDialog()})}
    private fun goalDialog(){val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};val name=input("Название");val target=input("Цель ₽","number");val days=input("Дней","number");listOf(name,target,days).forEach{b.addView(it)};AlertDialog.Builder(this).setTitle("Новая цель").setView(b).setPositiveButton("Сохранить"){_,_->val a=store.get("goals","").split("|").filter{it.isNotBlank()}.toMutableList();a.add("${name.text};${target.text};${income}");store.put("goals",a.joinToString("|"));render()}.setNegativeButton("Отмена",null).show()}
    private fun bonusDialog(){val b=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL};val n=input("Заказов","number");val a=input("Бонус ₽","number");listOf(n,a).forEach{b.addView(it)};AlertDialog.Builder(this).setTitle("Бонус за заказы").setView(b).setPositiveButton("Сохранить"){_,_->store.put("order_bonus","${n.text};${a.text}");toast("Бонус сохранён")}.setNegativeButton("Отмена",null).show()}

    private fun transport(){h("Транспорт");val name=input("Название транспорта");val cost=input("Стоимость ₽","number");val km=input("Пробег км","number");val interval=input("Сервис каждые км","number");listOf(name,cost,km,interval).forEach{content.addView(it)};addViewRow(button("Сохранить транспорт",0){store.put("vehicle","${name.text};${cost.text};${km.text};${interval.text}");toast("Сохранено")});card("Эксплуатационные расходы"){val fuel=input("Цена топлива ₽/л","number");val cons=input("Расход л/100 км","number");addView(fuel);addView(cons);addView(button("Рассчитать стоимость/км",0){val v=(fuel.text.toString().toDoubleOrNull()?:0.0)*(cons.text.toString().toDoubleOrNull()?:0.0)/100;toast("Топливо: ${money(v)} / км")})};card("Амортизация"){p("Стоимость транспорта / ресурс = ориентировочная стоимость км")}}

    private fun settings(){h("Настройки"); card("Тема"){val r=row();listOf("dark","light","cyber","forest","midnight").forEach{x->r.addView(button(x,0){theme=x;store.put("theme",x);root.setBackgroundColor(bg());render()})};addView(r)};card("Параметры"){val tax=input("Налог %","number");val tips=input("Чаевые ₽","number");addView(tax);addView(tips);addView(button("Сохранить",0){store.put("tax",tax.text.toString());store.put("tips",tips.text.toString());toast("Настройки сохранены")})};card("Данные"){addView(button("Экспорт настроек",0){exportJson()});addView(button("Импорт JSON",0){importJson()});addView(button("Месячный PDF-отчёт",0){exportPdf()});addView(button("Сбросить все данные",0){AlertDialog.Builder(this).setTitle("Сбросить?").setMessage("Все локальные данные будут удалены.").setPositiveButton("Сбросить"){_,_->store.clear();income=0.0;orders=0;distance=0.0;render()}.setNegativeButton("Отмена",null).show()})};card("Напоминание"){val time=input("Время","time");val text=input("Текст");addView(time);addView(text);addView(button("Запланировать",0){scheduleReminder(time.text.toString(),text.text.toString())})};card("Android API"){addView(button("Уведомление",0){notify("Courier Analytics Pro","Тестовое уведомление")});addView(button("Вибрация",0){vibrate()});addView(button("Получить геолокацию",0){startGps()});addView(button("Записать аудио",0){toggleRecord()});addView(button("Камера",0){startActivity(Intent("android.media.action.IMAGE_CAPTURE"))})}}

    private fun startShift(){shiftStarted=System.currentTimeMillis();store.put("activeShift",shiftStarted.toString());notify("Смена начата","Courier Analytics Pro");startGps()}
    private fun finishShift(){shiftStarted=0;breakStarted=0;shiftBreak=0;stopGps();store.remove("activeShift");toast("Смена завершена")}
    private fun toggleBreak(){if(shiftStarted==0L)return;if(breakStarted==0L)breakStarted=System.currentTimeMillis()else{shiftBreak+=System.currentTimeMillis()-breakStarted;breakStarted=0}}
    private fun shiftElapsed()=System.currentTimeMillis()-shiftStarted-shiftBreak-if(breakStarted>0)System.currentTimeMillis()-breakStarted else 0
    private fun net():Double{val tax=store.get("tax","0").toDoubleOrNull()?:0.0;val tips=store.get("tips","0").toDoubleOrNull()?:0.0;return income+tips-income*tax/100}
    private fun hourRate()=if(shiftElapsed()>0)net()/(shiftElapsed()/3600000.0) else 0.0

    private fun startGps(){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),101);return};locationManager=getSystemService(LOCATION_SERVICE) as LocationManager;try{locationManager?.requestLocationUpdates(LocationManager.GPS_PROVIDER,2000,2f,object:LocationListener{override fun onLocationChanged(l:Location){if(currentLocation!=null)distance+=currentLocation!!.distanceTo(l)/1000.0;currentLocation=l;route.add(l)}})}catch(_:Exception){toast("GPS недоступен")}}
    private fun stopGps(){try{locationManager?.removeUpdates(gpsListener)}catch(_:Exception){}} private val gpsListener=object:LocationListener{override fun onLocationChanged(l:Location){}}
    private fun exportGpx(){val sb=StringBuilder("<?xml version=\"1.0\"?><gpx version=\"1.1\" creator=\"Courier Analytics Pro\"><trk><name>Shift</name><trkseg>");route.forEach{sb.append("<trkpt lat=\"${it.latitude}\" lon=\"${it.longitude}\"/>")};sb.append("</trkseg></trk></gpx>");shareText(sb.toString(),"route.gpx")}

    private fun exportSlotsCsv(){val s="date;platform;orders;income\n"+store.get("slots","").split("|").filter{it.isNotBlank()}.joinToString("\n");shareText(s,"slots.csv")}
    private fun exportJson(){val s="""{"theme":"$theme","income":$income,"orders":$orders,"distance":$distance,"slots":"${esc(store.get("slots",""))}","expenses":"${esc(store.get("expenses",""))}","goals":"${esc(store.get("goals",""))}"}""";shareText(s,"courier-backup.json")}
    private fun importJson(){val i=Intent(Intent.ACTION_OPEN_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,202)}
    override fun onActivityResult(r:Int,c:Int,d:Intent?){super.onActivityResult(r,c,d);if(r==202&&c==RESULT_OK){try{val s=contentResolver.openInputStream(d!!.data!!)?.bufferedReader()?.readText()?:("");Regex("\\\"slots\\\":\\\"(.*?)\\\"").find(s)?.groupValues?.get(1)?.let{store.put("slots",it)};Regex("\\\"expenses\\\":\\\"(.*?)\\\"").find(s)?.groupValues?.get(1)?.let{store.put("expenses",it)};toast("Импортировано");render()}catch(e:Exception){toast("Ошибка импорта")}}}
    private fun exportPdf(){val doc=android.graphics.pdf.PdfDocument();val page=doc.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(595,842,1).create());val c=page.canvas;val paint=Paint().apply{textSize=22f;color=Color.BLACK};c.drawText("Courier Analytics Pro",32f,50f,paint);paint.textSize=14f;c.drawText("Отчёт: ${SimpleDateFormat("yyyy-MM-dd",Locale.US).format(Date())}",32f,80f,paint);c.drawText("Доход: ${money(income)}",32f,120f,paint);c.drawText("Заказы: $orders",32f,150f,paint);c.drawText("Пробег: ${fmt(distance)} км",32f,180f,paint);c.drawText("Чистыми: ${money(net())}",32f,210f,paint);doc.finishPage(page);val f=File(cacheDir,"courier-report.pdf");doc.writeTo(FileOutputStream(f));doc.close();shareFile(Uri.fromFile(f),"application/pdf")}

    private fun scheduleReminder(time:String,text:String){val parts=time.split(":");val cal=Calendar.getInstance().apply{set(Calendar.HOUR_OF_DAY,parts.getOrNull(0)?.toIntOrNull()?:9);set(Calendar.MINUTE,parts.getOrNull(1)?.toIntOrNull()?:0);set(Calendar.SECOND,0);if(timeInMillis<=System.currentTimeMillis())add(Calendar.DAY_OF_YEAR,1)};val pi=PendingIntent.getBroadcast(this,77,Intent(this,ReminderReceiver::class.java).putExtra("text",text),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE);(getSystemService(ALARM_SERVICE) as AlarmManager).setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,cal.timeInMillis,pi);toast("Напоминание запланировано")}
    private fun notify(title:String,msg:String){if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS),103);return};val nm=getSystemService(NOTIFICATION_SERVICE) as NotificationManager;if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel("courier","Courier Analytics",NotificationManager.IMPORTANCE_DEFAULT));nm.notify((System.currentTimeMillis()%100000).toInt(),Notification.Builder(this,"courier").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(msg).setAutoCancel(true).build())}
    private fun vibrate(){(getSystemService(VIBRATOR_SERVICE) as Vibrator).vibrate(VibrationEffect.createOneShot(300,VibrationEffect.DEFAULT_AMPLITUDE))}
    private fun toggleRecord(){if(recorder==null){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO),104);return};recordingFile=File(cacheDir,"rec-${System.currentTimeMillis()}.m4a");recorder=MediaRecorder(this).apply{setAudioSource(MediaRecorder.AudioSource.MIC);setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);setAudioEncoder(MediaRecorder.AudioEncoder.AAC);setOutputFile(recordingFile!!.absolutePath);prepare();start()};toast("Запись начата")}else{recorder?.stop();recorder?.release();recorder=null;toast("Запись сохранена: ${recordingFile?.name}")}}

    private fun shareText(s:String,name:String){val f=File(cacheDir,name);f.writeText(s);shareFile(Uri.fromFile(f),"text/plain")}
    private fun shareFile(u:Uri,type:String){val i=Intent(Intent.ACTION_SEND).setType(type).putExtra(Intent.EXTRA_STREAM,u).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);startActivity(Intent.createChooser(i,"Поделиться"))}

    private fun openDrawer(){val items=arrayOf("Dashboard","Слоты","Расходы","Цели","Транспорт","Настройки");AlertDialog.Builder(this).setTitle("Courier Analytics").setItems(items){_,w->tab=arrayOf("dashboard","slots","expenses","goals","transport","settings")[w];render()}.show()}
    private fun h(s:String){val t=TextView(this).apply{text=s;textSize=28f;setTextColor(fg());typeface=Typeface.DEFAULT_BOLD;setPadding(4,8,4,12)};content.addView(t)}
    private fun p(s:String){content.addView(TextView(this).apply{text=s;textSize=14f;setTextColor(sec());setPadding(4,2,4,10)})}
    private fun card(title:String,body:LinearLayout.()->Unit){val box=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(16,14,16,14);setBackgroundColor(surface());body();};val t=TextView(this).apply{text=title;textSize=17f;setTextColor(fg());typeface=Typeface.DEFAULT_BOLD;setPadding(0,0,0,10)};box.addView(t,0);val lp=LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,8,0,10);content.addView(box,lp)}
    private fun row()=LinearLayout(this).apply{orientation=LinearLayout.HORIZONTAL;gravity=Gravity.CENTER_VERTICAL}
    private fun addViewRow(v:View){content.addView(v,LinearLayout.LayoutParams(-1,52).apply{setMargins(0,4,0,4)})}
    private fun button(text:String,w:Int,on:()->Unit)=Button(this).apply{this.text=text;setTextColor(fg());setOnClickListener{on()};if(w>0)layoutParams=LinearLayout.LayoutParams(w,52)}
    private fun input(hint:String,type:String="text")=EditText(this).apply{this.hint=hint;setTextColor(fg());setHintTextColor(sec());textSize=15f;setPadding(12,6,12,6);if(type=="number")inputType=2 else if(type=="date")inputType=android.text.InputType.TYPE_CLASS_DATETIME else if(type=="time")inputType=android.text.InputType.TYPE_CLASS_DATETIME}
    private fun toast(s:String)=Toast.makeText(this,s,Toast.LENGTH_SHORT).show()
    private fun now()=SimpleDateFormat("HH:mm",Locale.getDefault()).format(Date())
    private fun duration(ms:Long):String{val s=max(0,ms/1000);return "%02d:%02d:%02d".format(s/3600,(s/60)%60,s%60)}
    private fun money(x:Double)="%.2f ₽".format(Locale.US,x);private fun fmt(x:Double)="%.2f".format(Locale.US,x)
    private fun esc(s:String)=s.replace("\\","\\\\").replace("\"","\\\"")
    private fun storeData(){store.put("stats","$income;$orders;$distance")}
    private fun bg()=when(theme){"light"->Color.rgb(248,250,252);"cyber"->Color.rgb(5,5,16);"forest"->Color.rgb(2,44,34);"midnight"->Color.rgb(11,12,21);else->Color.rgb(9,9,11)}
    private fun surface()=when(theme){"light"->Color.WHITE;"forest"->Color.rgb(6,78,59);"cyber"->Color.rgb(10,10,26);"midnight"->Color.rgb(20,25,40);else->Color.rgb(24,24,27)}
    private fun fg()=if(theme=="light")Color.rgb(15,23,42) else Color.rgb(250,250,250);private fun sec()=if(theme=="light")Color.rgb(71,85,105) else Color.rgb(161,161,170);private fun accent()=when(theme){"light"->Color.rgb(37,99,235);"forest"->Color.rgb(16,185,129);"cyber"->Color.CYAN;"midnight"->Color.rgb(102,252,241);else->Color.rgb(250,204,21)};private fun red()=Color.rgb(239,68,68)

    inner class ChartView(c:Context):View(c){val p=Paint(Paint.ANTI_ALIAS_FLAG);override fun onDraw(c:Canvas){super.onDraw(c);p.color=accent();p.strokeWidth=5f;val w=width.toFloat();val h=height.toFloat();val pts=10;var prevY=h*0.7f;for(i in 1..pts){val x=w*i/pts;val y=h*(0.2f+0.55f*(1-i.toFloat()/pts))+sin(i*1.7)*12;c.drawLine(w*(i-1)/pts,prevY,x,y.toFloat(),p);prevY=y.toFloat()}}override fun onMeasure(w:Int,h:Int){setMeasuredDimension(MeasureSpec.getSize(w),160)}}
}

class Store(ctx:Context){private val p=ctx.getSharedPreferences(PREF,Context.MODE_PRIVATE);fun get(k:String,d:String)=p.getString(k,d)?:d;fun put(k:String,v:String){p.edit().putString(k,v).apply()};fun remove(k:String){p.edit().remove(k).apply()};fun clear(){p.edit().clear().apply()}}

class ReminderReceiver:BroadcastReceiver(){override fun onReceive(c:Context,i:Intent){if(Build.VERSION.SDK_INT>=33&&c.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)return;val nm=c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager;if(Build.VERSION.SDK_INT>=26)nm.createNotificationChannel(NotificationChannel("courier","Courier Analytics",NotificationManager.IMPORTANCE_DEFAULT));nm.notify(77,Notification.Builder(c,"courier").setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle("Courier Analytics Pro").setContentText(i.getStringExtra("text")?:"Напоминание").build())}}
