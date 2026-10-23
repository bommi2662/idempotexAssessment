package com.fde.processor.repository;

import com.fde.processor.model.entity.StreamWatermark;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StreamWatermarkRepository extends JpaRepository<StreamWatermark, String> {
}
