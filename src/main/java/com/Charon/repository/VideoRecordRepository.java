package com.Charon.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.Charon.entity.VideoRecord;
import org.apache.ibatis.annotations.Mapper;

import java.util.Optional;

@Mapper
public interface VideoRecordRepository extends BaseMapper<VideoRecord> {
    default Optional<VideoRecord> findByJobId(String jobId) {
        LambdaQueryWrapper<VideoRecord> qw = new LambdaQueryWrapper<>();
        qw.eq(VideoRecord::getJobId, jobId).last("limit 1");
        return Optional.ofNullable(this.selectOne(qw));
    }
}