import { useState, useRef, useEffect } from 'react';
import {
  Box,
  TextField,
  IconButton,
  Typography,
  Avatar,
  Chip,
  Button,
} from '@mui/material';
import {
  Send as SendIcon,
  SmartToy as BotIcon,
  Person as UserIcon,
} from '@mui/icons-material';
import { lobsterApi } from '@/services/api';
import { useAuthStore } from '@/stores';

interface Message {
  id: string;
  type: 'user' | 'bot';
  content: string;
  timestamp: Date;
  phase?: 1 | 2;
}

const MAX_MESSAGES = 20;

const getUserStorageKey = (userID: string | undefined): string => {
  if (!userID) return 'lobster_chat_messages_anonymous';
  return `lobster_chat_messages_${userID}`;
};

const loadMessages = (userID: string | undefined): Message[] => {
  const key = getUserStorageKey(userID);
  try {
    const saved = localStorage.getItem(key);
    if (saved) {
      const parsed = JSON.parse(saved);
      return parsed.map((msg: any) => ({
        ...msg,
        timestamp: new Date(msg.timestamp),
      }));
    }
  } catch (e) {
    console.error('加载聊天记录失败:', e);
  }
  return [
    {
      id: 'welcome',
      type: 'bot',
      content: `你好，我是智能助手。

我可以帮您：

• 修改密码
• 查看个人信息
• 衣橱统计
• 全部衣服展示
• 推荐今日穿搭
• 清空衣橱

请告诉我您需要什么帮助。`,
      timestamp: new Date(),
    },
  ];
};

const saveMessages = (messages: Message[], userID: string | undefined) => {
  const key = getUserStorageKey(userID);
  try {
    const messagesToSave = messages.slice(-MAX_MESSAGES);
    localStorage.setItem(key, JSON.stringify(messagesToSave));
  } catch (e) {
    console.error('保存聊天记录失败:', e);
  }
};

export function LobsterChat() {
  const { user } = useAuthStore();
  const userID = user?.id;

  const [messages, setMessages] = useState<Message[]>(() => loadMessages(userID));
  const [isInitialized, setIsInitialized] = useState(false);

  useEffect(() => {
    saveMessages(messages, userID);
  }, [messages, userID]);

  useEffect(() => {
    if (userID) {
      setMessages(loadMessages(userID));
    }
  }, [userID]);

  useEffect(() => {
    if (messages.length > 0 && !isInitialized) {
      setIsInitialized(true);
      setTimeout(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
      }, 50);
    }
  }, [messages, isInitialized]);
  const [inputValue, setInputValue] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages]);

  const handleSend = async () => {
    const message = inputValue.trim();
    if (!message || isLoading) return;

    const userMessage: Message = {
      id: `user-${Date.now()}`,
      type: 'user',
      content: message,
      timestamp: new Date(),
    };

    setMessages((prev) => [...prev, userMessage]);
    setInputValue('');
    setIsLoading(true);

    try {
      const response = await lobsterApi.chat(message);

      const phase1Message: Message = {
        id: `bot-phase1-${Date.now()}`,
        type: 'bot',
        content: response.response_template.phase1,
        timestamp: new Date(),
        phase: 1,
      };

      setMessages((prev) => [...prev, phase1Message]);

      setTimeout(() => {
        const phase2Message: Message = {
          id: `bot-phase2-${Date.now()}`,
          type: 'bot',
          content: response.response_template.phase2,
          timestamp: new Date(),
          phase: 2,
        };
        setMessages((prev) => [...prev, phase2Message]);
        setIsLoading(false);
      }, 600);
    } catch (error: any) {
      const errorMessage: Message = {
        id: `bot-error-${Date.now()}`,
        type: 'bot',
        content: `抱歉，处理请求时出错了：${error?.message || '未知错误'}`,
        timestamp: new Date(),
      };
      setMessages((prev) => [...prev, errorMessage]);
      setIsLoading(false);
    }
  };

  const handleQuickAction = (action: string) => {
    setInputValue(action);
  };

  const handleKeyPress = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSend();
    }
  };

  return (
    <Box
      sx={{
        display: 'flex',
        flexDirection: 'column',
        borderRadius: 3,
        backdropFilter: 'blur(16px) saturate(180%)',
        WebkitBackdropFilter: 'blur(16px) saturate(180%)',
        backgroundColor: 'rgba(255, 255, 255, 0.5)',
        border: '1px solid rgba(255, 255, 255, 0.6)',
        boxShadow: '0 8px 32px 0 rgba(31, 38, 135, 0.1)',
        height: 480,
        overflow: 'hidden',
      }}
    >
      <Box
        sx={{
          px: 2.5,
          py: 1.75,
          display: 'flex',
          alignItems: 'center',
          gap: 1.5,
          borderBottom: '1px solid rgba(255, 255, 255, 0.4)',
        }}
      >
        <Avatar
          sx={{
            width: 38,
            height: 38,
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            color: 'white',
            fontSize: '1rem',
            boxShadow: '0 2px 8px rgba(102, 126, 234, 0.3)',
          }}
        >
          <BotIcon sx={{ fontSize: 20 }} />
        </Avatar>

        <Box>
          <Box sx={{ display: 'flex', alignItems: 'center', gap: 0.75 }}>
            <Typography
              variant="subtitle1"
              fontWeight={600}
              color="text.primary"
              fontSize="0.9rem"
            >
              智能助手
            </Typography>
            <Box
              sx={{
                width: 7,
                height: 7,
                borderRadius: '50%',
                bgcolor: '#4CAF50',
                boxShadow: '0 0 6px rgba(76, 175, 80, 0.5)',
              }}
            />
          </Box>
          <Typography
            variant="caption"
            sx={{
              color: 'text.secondary',
              fontSize: '0.68rem',
            }}
          >
            Moonshot 模型提供支持
          </Typography>
        </Box>
      </Box>

      <Box
        sx={{
          flex: 1,
          overflowY: 'auto',
          px: 2.5,
          py: 2,
          display: 'flex',
          flexDirection: 'column',
          gap: 1.5,
          '&::-webkit-scrollbar': {
            width: 4,
          },
          '&::-webkit-scrollbar-thumb': {
            backgroundColor: 'rgba(0,0,0,0.1)',
            borderRadius: 4,
          },
        }}
      >
        {messages.map((msg) => (
          <Box
            key={msg.id}
            sx={{
              display: 'flex',
              justifyContent: msg.type === 'user' ? 'flex-end' : 'flex-start',
              gap: 1,
              maxWidth: '85%',
            }}
          >
            {msg.type === 'bot' && (
              <Avatar
                sx={{
                  width: 26,
                  height: 26,
                  background: 'linear-gradient(135deg, rgba(102, 126, 234, 0.2) 0%, rgba(118, 75, 162, 0.2) 100%)',
                  color: 'primary.main',
                  fontSize: '.7rem',
                  mt: 'auto',
                  mb: 0.5,
                  flexShrink: 0,
                }}
              >
                <BotIcon sx={{ fontSize: 14 }} />
              </Avatar>
            )}

            <Box
              sx={{
                maxWidth: '100%',
                py: 1.25,
                px: 1.75,
                borderRadius: 2.5,
                backdropFilter: 'blur(8px)',
                backgroundColor: msg.type === 'user'
                  ? 'rgba(102, 126, 234, 0.15)'
                  : 'rgba(255, 255, 255, 0.5)',
                border: msg.type === 'user'
                  ? '1px solid rgba(102, 126, 234, 0.2)'
                  : '1px solid rgba(255, 255, 255, 0.4)',
                transition: 'all 0.2s ease',
              }}
            >
              {msg.phase && (
                <Chip
                  size="small"
                  label={
                    msg.phase === 1 ? '理解中...' : '已完成'
                  }
                  sx={{
                    mb: 0.75,
                    height: 20,
                    fontSize: '.65rem',
                    fontWeight: 500,
                    bgcolor: msg.phase === 1
                      ? 'rgba(102, 126, 234, 0.15)'
                      : 'rgba(76, 175, 80, 0.15)',
                    color: msg.phase === 1
                      ? '#667eea'
                      : '#4CAF50',
                    '& .MuiChip-label': {
                      px: 0.75,
                    },
                  }}
                />
              )}

              <Typography
                variant="body2"
                sx={{
                  whiteSpace: 'pre-line',
                  wordBreak: 'break-word',
                  lineHeight: 1.65,
                  color: 'text.primary',
                  fontSize: '0.82rem',
                }}
              >
                {msg.content}
              </Typography>

              <Typography
                variant="caption"
                sx={{
                  display: 'block',
                  mt: 0.75,
                  textAlign: msg.type === 'user' ? 'right' : 'left',
                  color: 'text.disabled',
                  fontSize: '.62rem',
                }}
              >
                {msg.timestamp.toLocaleTimeString('zh-CN', {
                  hour: '2-digit',
                  minute: '2-digit',
                })}
              </Typography>
            </Box>

            {msg.type === 'user' && (
              <Avatar
                sx={{
                  width: 26,
                  height: 26,
                  background: 'rgba(102, 126, 234, 0.15)',
                  color: 'primary.main',
                  fontSize: '.7rem',
                  mt: 'auto',
                  mb: 0.5,
                  flexShrink: 0,
                }}
              >
                <UserIcon sx={{ fontSize: 14 }} />
              </Avatar>
            )}
          </Box>
        ))}

        {isLoading && (
          <Box sx={{ display: 'flex', justifyContent: 'flex-start', gap: 1 }}>
            <Avatar
              sx={{
                width: 26,
                height: 26,
                background: 'linear-gradient(135deg, rgba(102, 126, 234, 0.2) 0%, rgba(118, 75, 162, 0.2) 100%)',
                color: 'primary.main',
                fontSize: '.7rem',
              }}
            >
              <BotIcon sx={{ fontSize: 14 }} />
            </Avatar>
            <Box
              sx={{
                py: 1.25,
                px: 1.75,
                borderRadius: 2.5,
                backdropFilter: 'blur(8px)',
                backgroundColor: 'rgba(255, 255, 255, 0.5)',
                border: '1px solid rgba(255, 255, 255, 0.4)',
                display: 'flex',
                alignItems: 'center',
                gap: 0.6,
              }}
            >
              {[0, 1, 2].map((i) => (
                <Box
                  key={i}
                  sx={{
                    width: 6,
                    height: 6,
                    borderRadius: '50%',
                    bgcolor: 'rgba(102, 126, 234, 0.5)',
                    animation: 'lobster-bounce 1.4s ease-in-out infinite',
                    animationDelay: `${i * 160}ms`,
                  }}
                />
              ))}
              <style>{`
                @keyframes lobster-bounce {
                  0%, 80%, 100% { transform: scale(0); opacity: 0.3; }
                  40% { transform: scale(1); opacity: 1; }
                }
              `}</style>
            </Box>
          </Box>
        )}

        <div ref={messagesEndRef} />
      </Box>

      <Box
        sx={{
          px: 2,
          pt: 1.25,
          pb: 1,
          display: 'flex',
          gap: 0.75,
          flexWrap: 'wrap',
          borderTop: '1px solid rgba(255, 255, 255, 0.3)',
        }}
      >
        {['修改密码', '我的信息', '衣橱统计', '全部衣服', '今日穿搭', '你能做什么'].map((action) => (
          <Button
            key={action}
            size="small"
            onClick={() => handleQuickAction(action === '我的信息' ? '查看我的信息' : action === '衣橱统计' ? '我有多少衣服' : action === '全部衣服' ? '我有哪些衣服' : action === '今日穿搭' ? '请给我推荐今日穿搭' : action === '修改密码' ? '我要修改密码' : '你能做什么')}
            sx={{
              fontSize: '.7rem',
              textTransform: 'none',
              borderColor: 'rgba(255, 255, 255, 0.5)',
              color: 'text.primary',
              borderRadius: 6,
              px: 1.25,
              py: 0.35,
              backgroundColor: 'rgba(255, 255, 255, 0.3)',
              backdropFilter: 'blur(8px)',
              '&:hover': {
                borderColor: 'rgba(102, 126, 234, 0.4)',
                backgroundColor: 'rgba(255, 255, 255, 0.5)',
              },
            }}
          >
            {action}
          </Button>
        ))}
      </Box>

      <Box
        sx={{
          p: 1.5,
          display: 'flex',
          gap: 1,
          alignItems: 'flex-end',
          borderTop: '1px solid rgba(255, 255, 255, 0.3)',
        }}
      >
        <Box
          sx={{
            flex: 1,
            borderRadius: 3,
            backdropFilter: 'blur(12px)',
            WebkitBackdropFilter: 'blur(12px)',
            backgroundColor: 'rgba(255, 255, 255, 0.5)',
            border: '1px solid rgba(255, 255, 255, 0.5)',
            '&:hover': {
              backgroundColor: 'rgba(255, 255, 255, 0.65)',
            },
            '&:focus-within': {
              backgroundColor: 'rgba(255, 255, 255, 0.7)',
              border: '1px solid rgba(102, 126, 234, 0.3)',
            },
          }}
        >
          <TextField
            fullWidth
            multiline
            maxRows={3}
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyPress={handleKeyPress}
            placeholder="输入您的问题..."
            variant="standard"
            disabled={isLoading}
            InputProps={{
              disableUnderline: true,
              sx: {
                px: 1.5,
                py: 0.75,
                fontSize: '0.84rem',
                '&::placeholder': {
                  color: 'text.secondary',
                  opacity: 0.7,
                },
              },
            }}
          />
        </Box>
        <IconButton
          onClick={handleSend}
          disabled={!inputValue.trim() || isLoading}
          sx={{
            width: 40,
            height: 40,
            background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
            color: 'white',
            boxShadow: '0 4px 12px rgba(102, 126, 234, 0.3)',
            transition: 'all 0.2s ease',
            '&:hover:not(:disabled)': {
              background: 'linear-gradient(135deg, #5a6fd1 0%, #6a4190 100%)',
              boxShadow: '0 6px 16px rgba(102, 126, 234, 0.4)',
              transform: 'scale(1.05)',
            },
            '&:active:not(:disabled)': {
              transform: 'scale(0.98)',
            },
            '&:disabled': {
              background: 'rgba(0, 0, 0, 0.1)',
              color: 'text.disabled',
            },
          }}
        >
          <SendIcon sx={{ fontSize: 18 }} />
        </IconButton>
      </Box>
    </Box>
  );
}