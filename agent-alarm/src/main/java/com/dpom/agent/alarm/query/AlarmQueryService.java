package com.dpom.agent.alarm.query;

import com.dpom.agent.alarm.domain.Alarm;
import com.dpom.agent.alarm.persistence.AlarmDao;
import com.dpom.agent.alarm.persistence.AlarmQuery;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 告警查询服务：分页过滤查询，keyset 游标。
 */
@Service
public class AlarmQueryService {

    private final AlarmDao alarmDao;

    /**
     * 构造查询服务。
     *
     * @param alarmDao 告警持久化
     */
    public AlarmQueryService(AlarmDao alarmDao) {
        this.alarmDao = alarmDao;
    }

    /**
     * 分页查询告警。
     *
     * @param query 查询参数
     * @return 分页结果
     */
    public AlarmPage query(AlarmQuery query) {
        List<Alarm> items = alarmDao.search(query);
        Long nextCursor = items.size() == query.limit() ? items.get(items.size() - 1).id() : null;
        return new AlarmPage(items, nextCursor);
    }
}
