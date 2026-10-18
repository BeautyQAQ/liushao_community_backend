package com.liushao.service;

import com.liushao.common.exception.ApiException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class CommunityMailer {
  private final ObjectProvider<JavaMailSender> sender;
  private final String host;
  private final String from;

  public CommunityMailer(ObjectProvider<JavaMailSender> sender,
      @Value("${spring.mail.host:}") String host, @Value("${app.mail.from:}") String from) {
    this.sender = sender;
    this.host = host;
    this.from = from;
  }

  public void requireConfigured() {
    if (host.isBlank() || from.isBlank() || sender.getIfAvailable() == null)
      throw new ApiException(503, "邮件服务尚未配置，请联系管理员");
  }

  public void sendCode(String recipient, String purpose, String code) {
    requireConfigured();
    var mail = new SimpleMailMessage();
    mail.setFrom(from);
    mail.setTo(recipient);
    mail.setSubject("流沙社区 · " + (purpose.equals("VERIFY") ? "验证邮箱" : "重置密码"));
    mail.setText("你的验证码为：" + code + "\n有效期为10分钟，仅可使用一次。请勿将验证码告知他人。\n如果不是你本人操作，请忽略此邮件。");
    try {
      sender.getObject().send(mail);
    } catch (MailException e) {
      throw new ApiException(503, "邮件暂时无法发送，请稍后重试");
    }
  }
}
