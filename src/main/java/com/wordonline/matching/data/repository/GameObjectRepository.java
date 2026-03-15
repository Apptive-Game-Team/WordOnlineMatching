package com.wordonline.matching.data.repository;

import com.wordonline.matching.data.entity.GameObject;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

public interface GameObjectRepository extends R2dbcRepository<GameObject, Long> {
}
