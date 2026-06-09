package com.nowait.domain.favorite.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.nowait.domain.favorite.entity.Favorite;
import com.nowait.domain.restaurant.entity.Restaurant;
import com.nowait.domain.user.entity.User;

public interface FavoriteRepository extends JpaRepository<Favorite, Long>{
	
	/* * [Optional - 옵셔널 해설]
     * 데이터가 있을 수도 있고, 없을 수도 있을 때 안전하게 감싸서 가져오는 주머니입니다.
     * "이 유저가 이 식당을 예전에 즐겨찾기 한 적이 있나?" 하고 단 한 건을 조회할 때 사용합니다.
     */
	Optional<Favorite> findByUserAndRestaurant(User user, Restaurant restaurant);
	
	/* * [existsBy - 존재 여부 확인 해설]
     * 복잡한 데이터를 다 안 꺼내고, 딱 "즐겨찾기가 존재하니? 안 하니?" 결과만 True/False로 알려줍니다.
     * 프론트엔드 화면에서 하트 불빛(🧡/🤍)을 켤지 끌지 결정할 때 아주 유용하게 쓰입니다.
     */
	boolean existsByUserAndRestaurant(User user, Restaurant restaurant);

	List<Favorite> findAllByUser(User user);

}
