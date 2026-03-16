package com.wordonline.matching.magic.repository;

import com.wordonline.matching.magic.domain.Magic;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

public interface MagicRepository extends R2dbcRepository<Magic, Long> {
}
