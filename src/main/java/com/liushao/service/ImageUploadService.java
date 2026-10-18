package com.liushao.service;

import com.liushao.common.exception.ApiException;
import com.liushao.dao.UmsUserDao;
import com.liushao.model.entity.UmsUser;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImageUploadService {
  private final Path directory;
  private final JdbcTemplate db;
  private final UmsUserService users;
  private final UmsUserDao userDao;
  public ImageUploadService(@Value("${app.uploads.directory:./data/uploads}") String directory,
      JdbcTemplate db, UmsUserService users, UmsUserDao userDao) {
    this.directory=Path.of(directory).toAbsolutePath().normalize(); this.db=db; this.users=users; this.userDao=userDao;
  }

  public Map<String,Object> upload(String username, MultipartFile file) {
    var user=users.requireUser(username);
    if (file.isEmpty() || file.getSize()>4*1024*1024) throw new ApiException(400,"请选择不超过4MB的PNG或JPEG图片");
    byte[] encoded;
    try (var input = new MemoryCacheImageInputStream(file.getInputStream())) {
      var readers=ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw new ApiException(400,"图片格式不支持或文件已损坏");
      var reader=readers.next();
      try {
        if (!Set.of("png","jpeg","jpg").contains(reader.getFormatName().toLowerCase(Locale.ROOT))) throw new ApiException(400,"仅支持PNG或JPEG图片");
        reader.setInput(input,true,true);
        int width=reader.getWidth(0),height=reader.getHeight(0);
        if (width<1 || height<1 || width>8192 || height>8192 || (long)width*height>20_000_000) throw new ApiException(400,"图片尺寸过大，请压缩后上传");
        var decoded=reader.read(0);
        var clean=new BufferedImage(width,height,BufferedImage.TYPE_INT_ARGB);
        var graphics=clean.createGraphics();
        try { graphics.drawImage(decoded,0,0,null); } finally { graphics.dispose(); decoded.flush(); }
        try (var output=new ByteArrayOutputStream()) {
          ImageIO.write(clean,"png",output);
          encoded=output.toByteArray();
        } finally { clean.flush(); }
        if (encoded.length>8*1024*1024) throw new ApiException(400,"图片处理后过大，请降低尺寸重试");
      } finally { reader.dispose(); }
    } catch (IOException e) { throw new ApiException(400,"图片无法读取，请重新选择"); }
    String name=UUID.randomUUID()+".png";
    Path output=directory.resolve(name);
    try {
      Files.createDirectories(directory);
      Files.write(output,encoded,StandardOpenOption.CREATE_NEW);
      db.update("INSERT INTO community_upload(filename,user_id,size_bytes) VALUES(?,?,?)",name,user.getId(),encoded.length);
    } catch (Exception e) {
      try { Files.deleteIfExists(output); } catch (IOException ignored) {}
      throw new ApiException(503,"图片存储暂时不可用，请稍后重试");
    }
    return Map.of("url","/uploads/"+name,"size",encoded.length,"contentType","image/png");
  }

  public Resource image(String name) {
    validName(name);
    Integer count=db.queryForObject("SELECT COUNT(*) FROM community_upload WHERE filename=?",Integer.class,name);
    var path=directory.resolve(name);
    if (count==null || count==0 || !Files.isRegularFile(path)) throw new ApiException(404,"图片不存在");
    return new FileSystemResource(path);
  }

  @Transactional
  public UmsUser avatar(String username, String url) {
    var user=users.requireUserForUpdate(username);
    if (url==null || !url.startsWith("/uploads/")) throw new ApiException(400,"请选择已上传的图片");
    String name=url.substring("/uploads/".length());
    validName(name);
    Integer count=db.queryForObject("SELECT COUNT(*) FROM community_upload WHERE filename=? AND user_id=?",Integer.class,name,user.getId());
    if (count==null || count==0 || !Files.isRegularFile(directory.resolve(name))) throw new ApiException(403,"只能使用自己上传的图片");
    user.setAvatar(url); user.setModifyTime(new Date());
    return userDao.save(user);
  }

  private void validName(String name) {
    if (!name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png")) throw new ApiException(404,"图片不存在");
  }
}
