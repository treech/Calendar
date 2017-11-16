package mi.email;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.CalendarContract;
import android.provider.CalendarContract.Events;
import android.provider.CalendarContract.Attendees;
import android.provider.CalendarContract.Calendars;
import android.provider.CalendarContract.Reminders;
import android.text.TextUtils;
import android.util.Log;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.TimeZone;

import javax.mail.BodyPart;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import static mi.email.IcalendarUtils.uncleanseString;

/**
 * 每封收到的邮件 是一个ReciveMail对象
 *
 **/
public class ResolveMail{
	private MimeMessage mineMsg = null;
	private boolean hasCalendar = false;
	private String fileName = "";
	private Context context;
	private static final boolean IS_AUTO = true;	//true:自动导入：false：手动导入
	private static final boolean DEBUG = true;
	private static final String TAG = "ResolveMail";

	private static String CALANDER_URL = "";
	private static String CALANDER_EVENT_URL = "";
	private static String CALANDER_REMIDER_URL = "";
	private static String CALANDER_ATTENDEES_URL = "";
	//为了兼容不同版本的日历,2.2以后url发生改变
	static{
		if(Integer.parseInt(Build.VERSION.SDK) >= 8){
			CALANDER_URL = "content://com.android.calendar/calendars";
			CALANDER_EVENT_URL = "content://com.android.calendar/events";
			CALANDER_REMIDER_URL = "content://com.android.calendar/reminders";
			CALANDER_ATTENDEES_URL = "content://com.android.calendar/attendees";

		}else{
			CALANDER_URL = "content://calendar/calendars";
			CALANDER_EVENT_URL = "content://calendar/events";
			CALANDER_REMIDER_URL = "content://calendar/reminders";
			CALANDER_ATTENDEES_URL = "content://calendar/attendees";
		}
	}

	/**
	 * 构造函数
	 *
	 * @param mimeMessage
	 */
	public  ResolveMail(MimeMessage mimeMessage) {
		this.mineMsg = mimeMessage;
	}

	public ResolveMail(Context context) {
		this.context = context;
	}

	// MimeMessage设定
	public synchronized void setMimeMessage(MimeMessage mimeMessage) throws Exception  {
		this.mineMsg = mimeMessage;
        sync();
	}

	/**
	 * 获得送信人的姓名和邮件地址
	 *
	 * @throws MessagingException
	 */
	public String getFrom() throws MessagingException {
		InternetAddress address[] = (InternetAddress[]) mineMsg.getFrom();
		String addr = address[0].getAddress();
		String name = address[0].getPersonal();

		if (addr == null) { addr = ""; }
		if (name == null) { name = ""; }
		String nameAddr = name + "<" + addr + ">";
		return nameAddr;
	}

	/**
	 * 是否包含日历附件
	 *
	 */
	public synchronized boolean isHaveCalender() {
		return hasCalendar;
	}

    /**
	 * 判断此邮件是否包含附件
	 */
	public synchronized boolean isContainAttach(Part part) throws Exception {
		boolean attachflag = false;
		String contentType = part.getContentType();
        if (part.isMimeType("multipart/*")) {
			Multipart mp = (Multipart) part.getContent();
			for (int i = 0; i < mp.getCount(); i++) {
				BodyPart mpart = mp.getBodyPart(i);
				String icsContent = inputStream2String(mpart.getInputStream()).trim();
//				Log.d(TAG, "########## isContainAttach: contentType = " + contentType);
//				Log.d(TAG, "############ ------isContainAttach: icsContent = " + String.valueOf(icsContent));
//				Log.d(TAG, "000==============================================================================000 ");
				String disposition = mpart.getDisposition();
				if ((disposition != null)
						&& ((disposition.equals(Part.ATTACHMENT))
						|| (disposition.equals(Part.INLINE))))
					attachflag = true;
				else if (mpart.isMimeType("multipart/*")) {
					attachflag = isContainAttach((Part) mpart);
				} else {
					String contype = mpart.getContentType();
//					Log.d(TAG, "111 ===================== mpart(getContentType()) = " + String.valueOf(contype));
					if (contype.toLowerCase().indexOf("calendar") != -1) {
						attachflag = true;
						hasCalendar = true;

						ParseCalendar(mpart.getInputStream());
					}
				}
			}
		} else if (part.isMimeType("message/rfc822")) {
			attachflag = isContainAttach((Part) part.getContent());
		}
		return attachflag;
	}

	/**
	 * 【保存附件】
	 */
	public synchronized void saveAttach(Part part) throws Exception {
        if (part.isMimeType("multipart/*")) {
			Multipart mp = (Multipart) part.getContent();
			for (int i = 0; i < mp.getCount(); i++) {
				BodyPart mPart = mp.getBodyPart(i);
				String disposition = mPart.getDisposition();
				if ((disposition != null)
						&& ((disposition.equals(Part.ATTACHMENT)) || (disposition
						.equals(Part.INLINE)))) {

				} else if (mPart.isMimeType("multipart/*")) {
                    saveAttach(mPart);
				} else {
                    String icsContent = inputStream2String(mPart.getInputStream()).trim();
					if (icsContent.startsWith("BEGIN:VCALENDAR")) {
						Log.d(TAG, "############ ------saveAttach: icsContent = " + String.valueOf(icsContent));
						Log.d(TAG, "111==============================================================================111 ");
						icsContent = inputStream2String(mPart.getInputStream()).trim()
								.replaceAll("\n ", "")
								.replaceAll("\n	", "")
								.replaceAll("\r", "")
								.replaceAll("DESCRIPTION:REMINDER", "")
								.replaceAll("DESCRIPTION:Reminder", "")
								.replaceAll(";TZID=China Standard Time", "")
								.replaceAll(";TZID=\"China Standard Time\"", "")
								.replaceAll(";VALUE=DATE", "")
								.replaceAll(";LANGUAGE=zh-CN", "")
								.replaceAll(";LANGUAGE=zh-cn", "");
						Log.d(TAG, "############ ------saveAttach: icsContent = " + String.valueOf(icsContent));
						Log.d(TAG, "222==============================================================================222 ");
						InputStream in = new ByteArrayInputStream(icsContent.getBytes("UTF-8"));
						if(IS_AUTO){
							autoInsertICS(this.context,in);		//auto-import calendar
						} else {
							setSaveFileName(getTime() + ".ics");
							saveFile(getSaveFileName(), in);	//manual-import calendar
						}
					}
				}
			}
		} else if (part.isMimeType("message/rfc822")) {
            saveAttach((Part) part.getContent());
		}
	}

	private synchronized void ParseCalendar(InputStream in) throws IOException {
		String icsContent = inputStream2String(in).trim();
		if (icsContent.startsWith("BEGIN:VCALENDAR")) {
//			Log.d(TAG, "############ ------saveAttach: icsContent = " + String.valueOf(icsContent));
//			Log.d(TAG, "111==============================================================================111 ");
			icsContent = icsContent
					.replaceAll("\n ", "")
					.replaceAll("\n	", "")
					.replaceAll("\r", "")
					.replaceAll("DESCRIPTION:REMINDER", "")
					.replaceAll("DESCRIPTION:Reminder", "")
					.replaceAll(";TZID=China Standard Time", "")
					.replaceAll(";TZID=\"China Standard Time\"", "")
					.replaceAll(";VALUE=DATE", "")
					.replaceAll(";LANGUAGE=zh-CN", "")
					.replaceAll(";LANGUAGE=zh-cn", "");
//			Log.d(TAG, "############ ------saveAttach: icsContent = " + String.valueOf(icsContent));
//			Log.d(TAG, "222==============================================================================222 ");
			InputStream ina = new ByteArrayInputStream(icsContent.getBytes("UTF-8"));
			if(IS_AUTO){
				autoInsertICS(this.context,ina);		//auto-import calendar
			} else {
				setSaveFileName(getTime() + ".ics");
				try {
					saveFile(getSaveFileName(), ina);	//manual-import calendar
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * 保存附件ics到本地
	 */
	public synchronized void sync() throws Exception {
		Part tmpPart = (Part) mineMsg;
		if (isContainAttach(tmpPart))	//如果包含附件
		{
			//if(isHaveCalender())
			{
                //saveAttach(tmpPart);
			}
		}
		else{
			if(DEBUG)Log.d(TAG, "############ ------isContainAttach: return false!!!!!!");
		}
	}

	/**
	 * 【真正的保存日历日程文件到指定目录里】
	 */
	private void saveFile(String fileName, InputStream in) throws Exception {
		String storeDir = getRootPath() + "/CalendarEvents";
		String separator = "/";
		File destDir = new File(storeDir);
		if (!destDir.exists()) {
			destDir.mkdirs();
		}
		File storeFile = new File(storeDir + separator + fileName);
		BufferedOutputStream bos = null;
		BufferedInputStream bis = null;
		try {
			bos = new BufferedOutputStream(new FileOutputStream(storeFile));
			bis = new BufferedInputStream(in);
			int c;
			while ((c = bis.read()) != -1) {
				bos.write(c);
				bos.flush();
			}
		} catch (Exception exception) {
			exception.printStackTrace();
			throw new Exception("文件保存失败!");
		} finally {
			bos.close();
			bis.close();
		}
	}

	/**
	 * 获取SDcard根目录
	 * */
	public String getRootPath() {
		return Environment.getExternalStorageDirectory().toString();
	}

	public String inputStream2String (InputStream in) throws IOException{
		StringBuffer out = new StringBuffer();
		byte[] b = new byte[4096];
		for (int n; (n = in.read(b)) != -1;) {
			out.append(new String(b, 0, n));
		}
		return out.toString();
	}

	public void setSaveFileName(String filename)
	{
		this.fileName = filename;
	}

	public String getSaveFileName()
	{
		return this.fileName;
	}

	/**
	 * 获取10位的时间戳
	 * */
	public String getTime(){
		long time = Calendar.getInstance().getTimeInMillis();
		String  str = String.valueOf(time);
		return str;
	}

    private int checkCalendarAccount(Context context) {
        Cursor userCursor = context.getContentResolver().query(Uri.parse(CALANDER_URL), null, null, null, null);
        try {
            if (userCursor == null)//查询返回空值
                return -1;
            int count = userCursor.getCount();
            if (count > 0) {//存在现有账户，取第一个账户的id返回
                userCursor.moveToFirst();
                return userCursor.getInt(userCursor.getColumnIndex(CalendarContract.Calendars._ID));
            } else {
                return -1;
            }
        } finally {
            if (userCursor != null) {
                userCursor.close();
            }
        }
    }

	private long getLocalTimeFromString(String iCalDate) {
		if(!iCalDate.contains("T"))iCalDate = iCalDate + "T000000";
		SimpleDateFormat format = new SimpleDateFormat("yyyyMMdd'T'HHmmss");

		//format.setTimeZone(TimeZone.getTimeZone("UTC"));
		try {
			format.parse(iCalDate);
			format.setTimeZone(TimeZone.getDefault());
			return format.getCalendar().getTimeInMillis();
		} catch (ParseException e) {
			e.printStackTrace();
		}
		return System.currentTimeMillis();
	}

	private void autoInsertICS(Context context, InputStream in)
	{
		ArrayList<String> contents = InputstreamToArraylist(in);
		VCalendar calendar = new VCalendar();
		calendar.populateFromString(contents);

		LinkedList<VEvent> events = calendar.getAllEvents();
		if (events == null) { return; }
		if (calendar == null) { return; }
		ContentValues event = new ContentValues();

		int calId = checkCalendarAccount(context);	// 获取日历账户的id
		Log.d(TAG, "###########################____calId = " + String.valueOf(calId));
		if (calId < 0) { return; } // 获取账户id失败直接返回，添加日历事件失败

		event.put(Events.CALENDAR_ID, calId);	// 插入账户的id
		VEvent firstEvent = calendar.getAllEvents().getFirst();

		event.put(Events.TITLE, uncleanseString(firstEvent.getProperty(VEvent.SUMMARY)));
		event.put(Events.EVENT_LOCATION, uncleanseString(firstEvent.getProperty(VEvent.LOCATION)));
		String body = firstEvent.getProperty("X-ALT-DESC;FMTTYPE=text/html");
		if (!TextUtils.isEmpty(body)) {
			//if(DEBUG)Log.d(TAG, "0############### body = " + String.valueOf(body));
			String tmp = body.replace("\\n\\n", "").replace("\\n", "");
			//if(DEBUG)Log.d(TAG, "1############### body = " + String.valueOf(tmp));
			event.put(Events.DESCRIPTION, tmp);
		}else{
			event.put(Events.DESCRIPTION, uncleanseString(firstEvent.getProperty(VEvent.DESCRIPTION)));
		}

		long start;
		long end;
		String dtStart = firstEvent.getProperty(VEvent.DTSTART);
		if (!TextUtils.isEmpty(dtStart)) {
			start = getLocalTimeFromString(dtStart);
		}else{
			start = Calendar.getInstance().getTimeInMillis();
		}
		event.put(Events.DTSTART, start);

		String dtEnd = firstEvent.getProperty(VEvent.DTEND);
		if (!TextUtils.isEmpty(dtEnd)) {
			end = getLocalTimeFromString(dtEnd);
		} else {
			end = Calendar.getInstance().getTimeInMillis();
		}
		event.put(Events.DTEND, end);

		String title = uncleanseString(firstEvent.getProperty(VEvent.SUMMARY));
		String location = uncleanseString(firstEvent.getProperty(VEvent.LOCATION));
		String description = uncleanseString(firstEvent.getProperty(VEvent.DESCRIPTION));

		if(DEBUG) {
			Log.d(TAG, "###### title = " + String.valueOf(title));
			Log.d(TAG, "###### start = " + String.valueOf(start));
			Log.d(TAG, "###### end = " + String.valueOf(end));
		}

		boolean in_calendar = QueryAllCalendar(context, title, location, description, String.valueOf(start), String.valueOf(end));
		if(in_calendar)
			return;

		String allDay = firstEvent.getProperty("X-MICROSOFT-CDO-ALLDAYEVENT");
		if(TextUtils.isEmpty(allDay))
		{
			event.put(Events.ALL_DAY, 0);
		} else {
			if(allDay.equals("FALSE")) {
				event.put(Events.ALL_DAY, 0);
			} else {
				event.put(Events.ALL_DAY, 1);
			}
		}

		event.put(Events.EVENT_TIMEZONE, TimeZone.getDefault().getID().toString() );  //这个是时区，必须有
		Uri newEvent = context.getContentResolver().insert(Uri.parse(CALANDER_EVENT_URL), event);	//添加事件
		if (newEvent == null) {return;}// 添加日历事件失败直接返回

		if (firstEvent.mAttendees.size() > 0) {
			for (Attendee attendee : firstEvent.mAttendees) {
				ContentValues values = new ContentValues();
				//values.put(Attendees.ATTENDEE_NAME, attendee.mName);
				values.put(Attendees.ATTENDEE_NAME, attendee.mEmail);
				values.put(Attendees.ATTENDEE_EMAIL, attendee.mEmail);
				values.put(Attendees.ATTENDEE_RELATIONSHIP, Attendees.RELATIONSHIP_ATTENDEE);
				values.put(Attendees.ATTENDEE_TYPE, Attendees.TYPE_OPTIONAL);
				values.put(Attendees.ATTENDEE_STATUS, Attendees.ATTENDEE_STATUS_ACCEPTED);
				values.put(Attendees.EVENT_ID, ContentUris.parseId(newEvent));
				context.getContentResolver().insert(Uri.parse(CALANDER_ATTENDEES_URL), values);
				//if(xx == null) { return; } // 添加邀请者失败直接返回
			}
		}

		ContentValues cv = new ContentValues();
		// 提前10分钟有提醒
		cv.put(Reminders.MINUTES, 10);
		//事件提醒的设定
		cv.put(Reminders.EVENT_ID, ContentUris.parseId(newEvent));
		cv.put(Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT);
		context.getContentResolver().insert(Uri.parse(CALANDER_REMIDER_URL), cv);
		//if(uri == null) { return; } // 添加闹钟提醒失败直接返回
	}

	private ArrayList<String> InputstreamToArraylist(InputStream in)
	{
		ArrayList<String> result = new ArrayList<String>();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(in));
			String line;
			while ((line = reader.readLine()) != null) {
				result.add(line);
			}
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return result;
	}

	public boolean QueryAllCalendar(Context context, String title, String location, String description, String start, String end)
	{
		//日历里面相应的Event的URI
		Uri uri = Uri.parse(CALANDER_EVENT_URL);
		ContentResolver cr = context.getContentResolver();
		Cursor cur = cr.query(uri, null, null, null, null);
		//日历里面相应的Event的ID
		while (cur.moveToNext()) {
			String calendarEventId = cur.getString(cur.getColumnIndex(Events._ID)); //日历里面相应的Event的title
			String calendarEventName = cur.getString(cur.getColumnIndex(Events.TITLE));
			String Location1 = cur.getString(cur.getColumnIndex(Events.EVENT_LOCATION));
			String description1 = cur.getString(cur.getColumnIndex(Events.DESCRIPTION));
			String dtstart1 = cur.getString(cur.getColumnIndex(Events.DTSTART));
			String dtend1 = cur.getString(cur.getColumnIndex(Events.DTEND));

			if(!DEBUG) {
				Log.d(TAG, "###### calendarEventId = " + String.valueOf(calendarEventId));
				Log.d(TAG, "###### calendarEventName = " + String.valueOf(calendarEventName));
				Log.d(TAG, "###### Location = " + String.valueOf(Location1));
				Log.d(TAG, "###### description = " + String.valueOf(description));
				Log.d(TAG, "###### dtstart = " + String.valueOf(dtstart1));
				Log.d(TAG, "###### dtend = " + String.valueOf(dtend1));
			}

			if(calendarEventName.equals(title)
					&& Location1.equals(location)
					&& description.equals(description)
					&& dtstart1.equals(String.valueOf(start))
					&& dtend1.equals(String.valueOf(end))
					) {
				Log.d(TAG, "###### return true = " + String.valueOf(true));
				return true;
			}
		}
		return false;
	}
}
