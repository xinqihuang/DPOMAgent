package com.dpom.agent.core.persistence.authority;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Optional;

/** Investigation 权威表的 MyBatis Mapper。 */
@Mapper
public interface InvestigationAuthorityDao {

    /** 插入当前头。 */
    int insertHead(AuthorityHeadRow row);

    /** 仅在数据库仍为预期版本时替换当前头。 */
    int updateHead(@Param("row") AuthorityHeadRow row, @Param("expectedVersion") long expectedVersion);

    /** 按标识读取当前头。 */
    Optional<AuthorityHeadRow> findHead(@Param("investigationId") String investigationId);

    /** 插入不可变版本。 */
    int insertRevision(AuthorityRevisionRow row);

    /** 按版本升序读取不可变历史。 */
    List<AuthorityRevisionRow> findRevisions(@Param("investigationId") String investigationId);

    /** 查询已落库审计的最大序号。 */
    Long findMaxAuditSequence(@Param("investigationId") String investigationId);

    /** 查询已落库 ToolUse 标识。 */
    List<String> findToolUseIds(@Param("investigationId") String investigationId);

    /** 插入追加审计。 */
    int insertAudit(AuthorityAuditRow row);

    /** 插入追加 ToolUse。 */
    int insertToolUse(AuthorityToolUseRow row);

    /** 按序号游标读取有界审计进度。 */
    List<AuthorityAuditViewRow> findAuditPage(@Param("investigationId") String investigationId,
                                              @Param("afterSequence") long afterSequence,
                                              @Param("limit") int limit);
}
