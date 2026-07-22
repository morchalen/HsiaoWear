import { useState, useEffect, useCallback } from 'react';
import {
  Box,
  Typography,
  Avatar,
  Skeleton,
  Chip,
  useTheme,
} from '@mui/material';
import { WbSunny, Cloud, AcUnit, WaterDrop, LocationOn, CalendarToday } from '@mui/icons-material';
import type { WeatherData } from '@/types';
import { OutfitRecommendation } from '@/components/wardrobe/OutfitRecommendation';
import { weatherApi, authApi } from '@/services/api';
import { useAuthStore } from '@/stores';
import { getGlassCard } from '@/styles/glass';

const WEATHER_ICONS: Record<string, React.ReactNode> = {
  sunny: <WbSunny sx={{ fontSize: '1.5rem' }} />,
  cloudy: <Cloud sx={{ fontSize: '1.5rem' }} />,
  rainy: <WaterDrop sx={{ fontSize: '1.5rem' }} />,
  snowy: <AcUnit sx={{ fontSize: '1.5rem' }} />,
};

const WEATHER_ICON_MAP: Record<string, string> = {
  '晴': 'sunny',
  '晴朗': 'sunny',
  '多云': 'cloudy',
  '阴': 'cloudy',
  '雨': 'rainy',
  '小雨': 'rainy',
  '中雨': 'rainy',
  '大雨': 'rainy',
  '雪': 'snowy',
  '小雪': 'snowy',
  '大雪': 'snowy',
};

export function TodayPage() {
  const theme = useTheme();
  const user = useAuthStore((state) => state.user);
  const userID = user?.id || '';
  const [weather, setWeather] = useState<{
    city: string;
    temperature: string;
    description: string;
    date_str: string;
    weekday: string;
  } | null>(null);
  const [bodyImageUrl, setBodyImageUrl] = useState<string | undefined>(undefined);

  const fetchWeather = useCallback(async () => {
    try {
      const res = await weatherApi.getWeather();
      setWeather(res);
    } catch (err) {
      console.error('获取天气失败:', err);
    }
  }, []);

  useEffect(() => {
    fetchWeather();

    authApi.getBodyImage()
      .then((res) => {
        if (res.body_image_url) {
          setBodyImageUrl(res.body_image_url);
        }
      })
      .catch(() => { });
  }, [fetchWeather]);

  const weatherKey = weather ? (WEATHER_ICON_MAP[weather.description] || 'sunny') : 'sunny';

  return (
    <Box sx={{
      minHeight: '100vh',
      p: 3,
      display: 'flex',
      flexDirection: 'column',
    }}>
      <Box sx={{
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        mb: 2,
      }}>
        <Box>
          <Typography variant="h6" component="h1" fontWeight={600}>
            今日穿搭
          </Typography>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75, mt: 0.5 }}>
            <CalendarToday sx={{ fontSize: '0.85rem', color: 'text.secondary' }} />
            <Typography variant="body2" color="text.secondary">
              {new Date().toLocaleDateString('zh-CN', {
                year: 'numeric',
                month: 'long',
                day: 'numeric',
              })}
              {' '}
              {weather?.weekday || ''}
            </Typography>
          </Box>
        </Box>

        {weather ? (
          <Box sx={{
            ...getGlassCard(theme),
            display: 'flex',
            alignItems: 'center',
            gap: 2,
            px: 2.5,
            py: 1.5,
          }}>
            <Avatar sx={{
              width: 52,
              height: 52,
              bgcolor: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
              color: 'white',
              borderRadius: 3,
              border: '1px solid rgba(255, 255, 255, 0.8)',
              boxShadow: '0 4px 15px rgba(102, 126, 234, 0.3)',
            }}>
              {WEATHER_ICONS[weatherKey]}
            </Avatar>
            <Box>
              <Box sx={{ display: 'flex', alignItems: 'baseline', gap: 0.5 }}>
                <Typography variant="h4" fontWeight={700} lineHeight={1} sx={{ color: 'primary.main' }}>
                  {weather.temperature}
                </Typography>
              </Box>
              <Typography variant="body2" color="text.secondary" sx={{ mt: 0.25 }}>
                {weather.description}
              </Typography>
            </Box>
            <Box sx={{ height: 36, width: '1px', bgcolor: 'rgba(0,0,0,0.1)', mx: 0.5 }} />
            <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.5 }}>
              <LocationOn sx={{ fontSize: '0.9rem', color: 'text.secondary' }} />
              <Typography variant="body2" color="text.secondary" fontWeight={500}>
                {weather.city}
              </Typography>
            </Box>
          </Box>
        ) : (
          <Skeleton width={200} height={80} variant="rectangular" sx={{ borderRadius: 3 }} />
        )}
      </Box>

      <OutfitRecommendation
        bodyImageUrl={bodyImageUrl}
        onBodyImageUpdate={setBodyImageUrl}
      />
    </Box>
  );
}