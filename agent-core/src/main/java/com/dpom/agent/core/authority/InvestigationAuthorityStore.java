package com.dpom.agent.core.authority;

import java.util.List;
import java.util.Optional;

/**
 * Investigation 权威聚合的持久化端口。
 *
 * <p>实现必须同时保存当前头、不可变版本历史、ToolUse 和审计历史。</p>
 */
public interface InvestigationAuthorityStore {

    /** 创建一个尚未持久化的聚合。 */
    void create(InvestigationAuthority authority);

    /** 以调用方读取到的版本为条件保存新版本。 */
    void save(InvestigationAuthority authority, long expectedVersion);

    /** 读取并校验当前权威版本。 */
    Optional<InvestigationAuthority> find(AuthorityId investigationId);

    /** 按版本升序读取并校验全部不可变快照。 */
    List<InvestigationAuthority.Snapshot> history(AuthorityId investigationId);
}

