package edu.unsw.core.config.mybatisplus;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import edu.unsw.core.domain.pojo.CurrentUser;
import edu.unsw.core.utils.CurrentUserUtils;
import org.apache.ibatis.reflection.MetaObject;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Objects;

/**
 * @author 徐一杰 YijieXu Bubble
 * @date 2026/6/21 12:29
 * @description MybatisPlus auto-fill configuration
 */
public class MyMetaObjectHandler implements MetaObjectHandler {
    @Override
    public void insertFill(MetaObject metaObject) {
        LocalDateTime localDateTime = LocalDateTime.now(ZoneId.systemDefault());
        this.strictInsertFill(metaObject, "createTime", LocalDateTime.class, localDateTime);
        this.strictInsertFill(metaObject, "isDelete", Boolean.class, false);
        this.strictInsertFill(metaObject, "isEnable", Boolean.class, true);
        this.strictInsertFill(metaObject, "isCanceled", Boolean.class, false);
        this.strictInsertFill(metaObject, "isAvailable", Boolean.class, true);
        this.strictInsertFill(metaObject, "isRead", Boolean.class, false);
        this.strictInsertFill(metaObject, "isDelivery", Boolean.class, false);
        this.strictInsertFill(metaObject, "status", Integer.class, 0);
        this.strictInsertFill(metaObject, "version", Integer.class, 0);
        this.strictInsertFill(metaObject, "permissionPriority", Integer.class, 4);
        // Creator
        CurrentUser currentUser = CurrentUserUtils.getUser();
        if (Objects.nonNull(currentUser)) {
            this.strictInsertFill(metaObject, "creator", Long.class, currentUser.getId());
        }
    }

    @Override
    public void updateFill(MetaObject metaObject) {
        LocalDateTime localDateTime = LocalDateTime.now(ZoneId.systemDefault());
        this.strictUpdateFill(metaObject, "editTime", LocalDateTime.class, localDateTime);
    }

}
