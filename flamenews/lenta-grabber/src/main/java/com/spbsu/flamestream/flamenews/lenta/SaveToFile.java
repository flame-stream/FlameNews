package com.spbsu.flamestream.flamenews.lenta;

import com.spbsu.flamestream.flamenews.lenta.model.News;

interface SaveToFile {
  void save(News news);
}