package com.dianping.puma.biz.service.impl;

import com.dianping.puma.biz.dao.CheckTaskDao;
import com.dianping.puma.biz.entity.CheckTaskEntity;
import com.dianping.puma.biz.service.CheckTaskService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CheckTaskServiceImpl implements CheckTaskService {

	@Autowired
	CheckTaskDao checkTaskDao;

	@Override
	public CheckTaskEntity findById(int id) {
		return checkTaskDao.findById(id);
	}

	@Override
	public List<CheckTaskEntity> findAll() {
		return checkTaskDao.findAll();
	}

	@Override
	public int update(CheckTaskEntity checkTaskEntity) {
		return checkTaskDao.update(checkTaskEntity);
	}
}