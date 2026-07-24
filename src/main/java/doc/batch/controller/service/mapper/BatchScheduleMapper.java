// =============================================
// BatchScheduleMapper.java
// =============================================
package doc.batch.controller.service.mapper;

import doc.batch.controller.service.BatchRunHistVo;
import doc.batch.controller.service.BatchScheduleVo;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;
import java.util.Map;

@Mapper
public interface BatchScheduleMapper {

    /** 활성 스케줄 조회 (페이징) - params: offset, pageSize */
    List<BatchScheduleVo> selectActiveSchedules(Map<String, Object> params);

    /** 활성 스케줄 총 건수 */
    int selectActiveSchedulesCount();

    /** 활성 스케줄 전체 조회 (페이징 미적용) - DynamicScheduler 크론 재등록 전용 */
    List<BatchScheduleVo> selectActiveSchedulesForScheduler();

    /** 전체 스케줄 조회 (사용여부 무관, 페이징) - params: offset, pageSize */
    List<BatchScheduleVo> selectAllSchedules(Map<String, Object> params);

    /** 전체 스케줄 총 건수 (사용여부 무관) */
    int selectAllSchedulesCount();

    /** 단건 조회 */
    BatchScheduleVo selectSchedule(String jobName);

    /** 신규 스케줄 등록 (동적 Job 등록) */
    int insertSchedule(BatchScheduleVo vo);

    /** 스케줄 전체 수정 (크론 + 동적 Job 설정 포함) */
    int updateSchedule(BatchScheduleVo vo);

    /** 사용여부 수정 */
    int updateUseYn(BatchScheduleVo vo);

    /** 스케줄 삭제 */
    int deleteSchedule(String jobName);

    /** 실행 이력 등록 */
    int insertRunHist(BatchRunHistVo vo);

    /** 실행 이력 종료 업데이트 */
    int updateRunHist(BatchRunHistVo vo);

    /** 실행 이력 상태 변경 (RUNNING → FAILED) */
    int updateAbandonedRunHist(String instanceId);

    /** 실행 이력 목록 (jobName/기간/상태 검색, 페이징) - params: jobName, startDt, endDt, status, offset, pageSize
     *  jobName은 대소문자 무시 부분일치(LIKE)이며, 특정 Job 조회(getHistory)는 정확한 jobName을,
     *  전체 조회(getAllHistory)는 빈 값을 넘겨 조건을 생략한다. */
    List<BatchRunHistVo> selectRunHistList(Map<String, Object> params);

    /** 실행 이력 총 건수 (jobName/기간/상태 검색) - params: jobName, startDt, endDt, status */
    int selectRunHistListCount(Map<String, Object> params);
}
