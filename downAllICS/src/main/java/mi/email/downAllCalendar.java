package mi.email;

import android.content.Context;

import java.util.Properties;
import java.util.concurrent.ExecutorService;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeMessage;

public class downAllCalendar {
	private String receive_host = "";
	private String port = "";
	private  String name = "";
	private String pass = "";
	private ExecutorService executorService = new PriorityExecutor(1, true);
	private int flag = 0;
	private Context context;

	/**
	 *  设置一些相关参数
	 * @param port
	 * @param name
	 * @param pass
	 * @param receive_host
	 */
	public void setMail(Context context, String receive_host, String port, String name, String pass) throws Exception {
		this.context = context;
		this.receive_host = receive_host;
		this.port = port;
		this.name = name;
		this.pass = pass;
		fetchAllMail();
	}

	private Context getContext()
	{
		return this.context;
	}

	public void fetchAllMail() throws Exception {
		Properties props = new Properties();
		Session session = Session.getDefaultInstance(props); // 取得pop3协议的邮件服务器
		Store store = session.getStore("pop3");

		//连接邮件服务器
		store.connect(this.receive_host, this.name, this.pass); // 返回文件夹对象
		Folder folder = store.getFolder("INBOX"); // 设置仅读
		folder.open(Folder.READ_ONLY); // 获取信息
		Message message[] = folder.getMessages();

		//通过for语句将读取到的邮件内容解析并下载
		for (int i = 0; i < message.length; i++) {
			doRun( (MimeMessage)message[i] );
		}
		if(getFlag() >= message.length)
		{
			setFlag(0);
			if(folder != null)folder.close(true);
			if(store != null)store.close();
		}
	}

	private  void doRun(final MimeMessage mimeMessage)
	{
		PriorityRunnable priorityRunnable = new PriorityRunnable(Priority.NORMAL, new Runnable() {
			@Override
			public void run() {
				try {
					ResolveMail resolveMail = new ResolveMail(getContext());
					resolveMail.setMimeMessage(mimeMessage);
				} catch (Exception e) {
					e.printStackTrace();
				}
				addFlag();
			}
		});
		executorService.execute(priorityRunnable);
	}

	private void addFlag()
	{
		this.flag = this.flag + 1;
	}

	private void setFlag(int flag)
	{
		this.flag = flag;
	}

	private int getFlag()
	{
		return this.flag;
	}
}
