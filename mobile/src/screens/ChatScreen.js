import React, { useEffect, useRef, useState } from 'react';
import {
  View,
  StyleSheet,
  FlatList,
  KeyboardAvoidingView,
  Platform,
  TextInput,
  Image,
  Alert,
  TouchableOpacity,
  Text as RNText,
  Animated,
  Easing,
  PanResponder,
} from 'react-native';
import {
  Text,
  IconButton,
  Snackbar,
  ActivityIndicator,
  Portal,
  Dialog,
  RadioButton,
  Button,
} from 'react-native-paper';
import * as ImagePicker from 'expo-image-picker';
import { Audio } from 'expo-av';
import * as FileSystem from 'expo-file-system';
import * as Clipboard from 'expo-clipboard';
// 自实现的 KaTeX-WebView 渲染组件，兼容 RN 0.76 New Architecture / Bridgeless
// （react-native-math-view 在 Bridgeless 下会抛 "Property 'require' doesn't exist"）
import { MathText } from '../components/MathText';
import {
  getMessages,
  sendMessage,
  sendMessageStream,
  transcribeAudio,
  synthesizeSpeech,
  bindSessionKnowledgeBase,
  toggleThinking,
  patchMessageRender,
} from '../api/chat';
import { getKnowledgeBases } from '../api/knowledgeBase';
import { colors, radii, spacing, shadows, typography } from '../theme';
import { warmupCache, setCached } from '../components/math/mathCache';
import { prerenderMessage, hasMath } from '../components/math/renderLatex';

/**
 * 二级页面：对话详情
 * route.params: { sessionId: number|null, title: string }
 *   - sessionId 为 null 时 = 新对话，第一次发送会自动创建
 */
export default function ChatScreen({ route, navigation }) {
  const initialSessionId = route?.params?.sessionId || null;
  const initialTitle = route?.params?.title || '新对话';
  const initialKbId = route?.params?.knowledgeBaseId ?? null;

  const [sessionId, setSessionId] = useState(initialSessionId);
  const [selectedKbId, setSelectedKbId] = useState(initialKbId);
  const [kbList, setKbList] = useState([]);
  const [kbDialogVisible, setKbDialogVisible] = useState(false);
  const [messages, setMessages] = useState([]);
  const [inputValue, setInputValue] = useState('');
  const [loading, setLoading] = useState(false);
  const [loadingHistory, setLoadingHistory] = useState(false);
  const streamingRef = useRef(false);
  const [expandedReasoning, setExpandedReasoning] = useState({});
  const [thinkingEnabled, setThinkingEnabled] = useState(route?.params?.thinkingEnabled ?? false);

  // 长按弹出小图标栏
  const [popoverVisible, setPopoverVisible] = useState(false);
  const [popoverMsg, setPopoverMsg] = useState(null); // { content, role, index }
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');
  const [selectedImage, setSelectedImage] = useState(null);

  // ── 语音输入状态 ──────────────────────────────
  const [recording, setRecording] = useState(null);          // Audio.Recording instance
  const [isRecording, setIsRecording] = useState(false);
  const [isTranscribing, setIsTranscribing] = useState(false);
  const [recordSec, setRecordSec] = useState(0);             // 当前录音秒数（仅 UI 显示）
  const [willCancel, setWillCancel] = useState(false);       // 上滑预取消状态
  const recordTimerRef = useRef(null);
  const recordingRef = useRef(null);                         // ref 版本，给异步流程和卸载使用
  const recordStartTsRef = useRef(0);                        // 录音真实开始时间戳（避免闭包陷阱）
  const willCancelRef = useRef(false);
  const stoppingRef = useRef(false);                         // 正在停止中，防双击重复 unload
  const stopPendingRef = useRef(false);                      // 录音未开始就收到停止指令
  const pulseAnim = useRef(new Animated.Value(1)).current;

  // 消息列表 ref
  const listRef = useRef(null);
  // 是否已完成首次"贴底"定位（历史消息加载后）
  const initialScrolledRef = useRef(false);
  // 上一次的消息条数；条数增加 = 新消息到来 → 滚到底
  // 这里只跟 messages.length 联动，不再监听 onContentSizeChange，
  // 避免被 MathText 内 WebView 异步测高反复唤醒造成"上下跳"循环
  const lastMsgCountRef = useRef(0);
  // 首次加载 3s 内 onContentSizeChange 触发追底；null = 已关闭
  const scrollSettleRef = useRef(null);

  // ── TTS 朗读状态 ───────────────────────────────
  // 同一时刻只允许一段语音播放；ttsLoadingIndex 表示正在请求音频的消息下标，
  // ttsPlayingIndex 表示正在播放的消息下标。
  const [ttsLoadingIndex, setTtsLoadingIndex] = useState(null);
  const [ttsPlayingIndex, setTtsPlayingIndex] = useState(null);
  const ttsSoundRef = useRef(null);

  // 卸载时停止 TTS 播放，防止泄漏
  useEffect(() => {
    return () => {
      if (ttsSoundRef.current) {
        ttsSoundRef.current.unloadAsync().catch(() => {});
        ttsSoundRef.current = null;
      }
    };
  }, []);

  // ── 弹出小图标操作栏（由 ⋮ 按钮触发） ──
  const showPopover = (item, index) => {
    if (!item.content) return;
    setPopoverMsg({ content: item.content, role: item.role, index });
    setPopoverVisible(true);
  };

  const dismissPopover = () => {
    setPopoverVisible(false);
    setPopoverMsg(null);
  };

  const handlePopoverFavorite = () => {
    dismissPopover();
    setSnackMsg('收藏功能即将上线');
    setSnackVisible(true);
  };

  const handlePopoverShare = () => {
    dismissPopover();
    setSnackMsg('分享功能即将上线');
    setSnackVisible(true);
  };

  /**
   * 朗读 / 停止朗读某条 AI 回复。
   * 同条消息再点一次 = 停止；点别的消息 = 切换。
   */
  const handleToggleTts = async (text, index) => {
    // 同条正在播 → 停止
    if (ttsPlayingIndex === index && ttsSoundRef.current) {
      try { await ttsSoundRef.current.stopAsync(); } catch {}
      try { await ttsSoundRef.current.unloadAsync(); } catch {}
      ttsSoundRef.current = null;
      setTtsPlayingIndex(null);
      return;
    }
    // 别条还在播 → 先卸掉
    if (ttsSoundRef.current) {
      try { await ttsSoundRef.current.unloadAsync(); } catch {}
      ttsSoundRef.current = null;
      setTtsPlayingIndex(null);
    }

    if (!text || !text.trim()) return;
    try {
      setTtsLoadingIndex(index);
      const res = await synthesizeSpeech(text);
      const audioBase64 = res?.data?.audio;
      const mimeType = res?.data?.mimeType || 'audio/wav';
      if (!audioBase64) throw new Error('未获取到音频');

      const uri = `data:${mimeType};base64,${audioBase64}`;
      const { sound } = await Audio.Sound.createAsync(
        { uri },
        { shouldPlay: true }
      );
      ttsSoundRef.current = sound;
      setTtsPlayingIndex(index);

      sound.setOnPlaybackStatusUpdate((status) => {
        if (status?.didJustFinish) {
          sound.unloadAsync().catch(() => {});
          if (ttsSoundRef.current === sound) ttsSoundRef.current = null;
          setTtsPlayingIndex((cur) => (cur === index ? null : cur));
        }
      });
    } catch (err) {
      // 后端 GlobalExceptionHandler 把 RuntimeException 的 message 放在 response.data.message
      const backendMsg = err?.response?.data?.message;
      const detail = backendMsg || err?.message || '朗读失败';
      setSnackMsg('朗读失败：' + detail);
      setSnackVisible(true);
      setTtsPlayingIndex(null);
    } finally {
      setTtsLoadingIndex(null);
    }
  };

  // 把 header title 改成当前会话名
  useEffect(() => {
    navigation.setOptions({ title: initialTitle });
  }, [initialTitle]);

  // 加载历史消息
  const loadMessages = async (sid) => {
    // 切换会话时重置滚动标记，让新会话能再次执行首次贴底
    initialScrolledRef.current = false;
    lastMsgCountRef.current = 0;
    if (!sid) {
      setMessages([]);
      return;
    }
    setLoadingHistory(true);
    try {
      const res = await getMessages(sid);
      const list = res.data || [];
      setMessages(list);
      // 后台预热公式渲染缓存（无 contentHtml 的消息异步渲染 + 补传后端）
      warmupCache(list, (msg, segments) => {
        const meta = JSON.stringify(segments);
        // 将 segments 存入本地缓存
        setCached(msg.content, segments, 15).catch(() => {});
        // 补传后端
        if (msg.id) {
          patchMessageRender(msg.id, {
            contentHtml: meta,
            renderMeta: meta,
          }).catch(() => {});
        }
      }, 15, 4);
    } catch (err) {
      setSnackMsg('加载消息失败');
      setSnackVisible(true);
    } finally {
      setLoadingHistory(false);
    }
  };

  useEffect(() => {
    if (sessionId == null) return;
    if (streamingRef.current) return;
    loadMessages(sessionId);
  }, [sessionId]);

  // ── 滚动策略 ───────────────────────────────
  // · 首次加载：3s 内任何内容尺寸变化都追底（覆盖 WebView 公式段测高窗口）
  // · 3s 后关闭自动追底，用户自由滑动不受干扰
  // · 用户发送新消息：立即贴底
  useEffect(() => {
    if (messages.length === 0) return;
    if (!initialScrolledRef.current) {
      initialScrolledRef.current = true;
      lastMsgCountRef.current = messages.length;
      // 首次：立刻滚到底，然后开启 3s 追底窗口
      requestAnimationFrame(() => {
        listRef.current?.scrollToEnd({ animated: false });
      });
      scrollSettleRef.current = true;
      const timer = setTimeout(() => {
        scrollSettleRef.current = null;
      }, 4000);
      return () => clearTimeout(timer);
    }
    if (messages.length > lastMsgCountRef.current) {
      lastMsgCountRef.current = messages.length;
      requestAnimationFrame(() => {
        listRef.current?.scrollToEnd({ animated: true });
      });
    }
  }, [messages.length]);

  // 加载用户的知识库列表（用于顶部选择器）
  useEffect(() => {
    (async () => {
      try {
        const res = await getKnowledgeBases();
        setKbList(res.data || []);
      } catch (err) {
        // 静默失败；用户可稍后重试
      }
    })();
  }, []);

  // 选择/切换知识库：若已有 session，立即写到后端；否则先记住，等 session 建立后再绑
  const handlePickKb = async (kbId) => {
    setSelectedKbId(kbId);
    setKbDialogVisible(false);
    if (sessionId) {
      try {
        await bindSessionKnowledgeBase(sessionId, kbId);
      } catch (err) {
        setSnackMsg(err.message || '切换知识库失败');
        setSnackVisible(true);
      }
    }
  };

  const handleToggleThinking = async () => {
    if (!sessionId) {
      setSnackMsg('请先发送一条消息建立会话');
      setSnackVisible(true);
      return;
    }
    const newVal = !thinkingEnabled;
    setThinkingEnabled(newVal);
    try {
      await toggleThinking(sessionId, newVal);
    } catch (err) {
      setThinkingEnabled(!newVal);
    }
  };

  const selectedKb = kbList.find((k) => k.id === selectedKbId);

  // ── 录音：按住开始，松开结束 → 上传识别 → 填入输入框 ──
  const startRecording = async () => {
    if (recordingRef.current || stoppingRef.current) return; // 已在录音中或正在停止
    stopPendingRef.current = false;
    try {
      if (Platform.OS === 'web') {
        setSnackMsg('Web 端暂不支持录音，请在手机上使用');
        setSnackVisible(true);
        return;
      }
      const perm = await Audio.requestPermissionsAsync();
      if (!perm.granted) {
        setSnackMsg('需要麦克风权限才能语音输入');
        setSnackVisible(true);
        return;
      }
      await Audio.setAudioModeAsync({
        allowsRecordingIOS: true,
        playsInSilentModeIOS: true,
      });
      const rec = new Audio.Recording();
      await rec.prepareToRecordAsync(Audio.RecordingOptionsPresets.HIGH_QUALITY);
      await rec.startAsync();

      // 如果在准备录音期间用户已松手，直接停止并退出，防止 UI 卡死在录音状态
      if (stopPendingRef.current) {
        await rec.stopAndUnloadAsync().catch(() => {});
        stopPendingRef.current = false;
        return;
      }

      recordingRef.current = rec;
      recordStartTsRef.current = Date.now();
      willCancelRef.current = false;
      setRecording(rec);
      setIsRecording(true);
      setRecordSec(0);
      setWillCancel(false);

      // 秒数计时（仅供 UI 显示，上限 60s 自动停止）
      recordTimerRef.current = setInterval(() => {
        setRecordSec((s) => {
          if (s >= 60) {
            stopRecording({ cancel: false });
            return s;
          }
          return s + 1;
        });
      }, 1000);

      // 麦克风呼吸灯
      Animated.loop(
        Animated.sequence([
          Animated.timing(pulseAnim, { toValue: 1.25, duration: 500, easing: Easing.ease, useNativeDriver: true }),
          Animated.timing(pulseAnim, { toValue: 1,    duration: 500, easing: Easing.ease, useNativeDriver: true }),
        ])
      ).start();
    } catch (err) {
      setSnackMsg('无法启动录音：' + (err?.message || ''));
      setSnackVisible(true);
      setIsRecording(false);
      setRecording(null);
      recordingRef.current = null;
    }
  };

  const stopRecording = async ({ cancel = false } = {}) => {
    const rec = recordingRef.current;
    if (!rec) {
      // 录音尚未开始（startRecording 还在异步准备中），标记让 start 自行取消
      stopPendingRef.current = true;
      return;
    }
    if (stoppingRef.current) return; // 正在停止中，防止双击重复 unload
    stoppingRef.current = true;

    // 第一阶段：停止录制并清理录音状态（无论成功与否都要复位）
    let uri = null;
    let durSec = 0;
    let shouldCancel = cancel || willCancelRef.current;
    try {
      if (recordTimerRef.current) {
        clearInterval(recordTimerRef.current);
        recordTimerRef.current = null;
      }
      pulseAnim.stopAnimation();
      pulseAnim.setValue(1);

      await rec.stopAndUnloadAsync();
      uri = rec.getURI();
      const startTs = recordStartTsRef.current || 0;
      const durMs = startTs > 0 ? Date.now() - startTs : 0;
      durSec = durMs / 1000;
    } catch (e) {
      // stopAndUnloadAsync 可能因重复调用抛出 "already unloaded"，静默吞下
    } finally {
      recordingRef.current = null;
      recordStartTsRef.current = 0;
      willCancelRef.current = false;
      stoppingRef.current = false;
      setIsRecording(false);
      setRecording(null);
      setRecordSec(0);
      setWillCancel(false);
    }

    if (shouldCancel || !uri) return;
    if (durSec < 0.8) {
      setSnackMsg('录音时间太短，请按住多说一会');
      setSnackVisible(true);
      return;
    }

    // 第二阶段：上传识别（独立于录音清理，不阻塞按钮恢复）
    setIsTranscribing(true);
    try {
      const base64 = await FileSystem.readAsStringAsync(uri, {
        encoding: FileSystem.EncodingType.Base64,
      });
      const format = uri.toLowerCase().endsWith('.wav') ? 'wav' : 'm4a';

      const res = await transcribeAudio(base64, format);
      const text = res?.data?.text || '';
      if (!text) {
        setSnackMsg('未识别到语音，请重试');
        setSnackVisible(true);
      } else {
        setInputValue((prev) => (prev ? prev + text : text));
      }
    } catch (err) {
      setSnackMsg('识别失败：' + (err?.response?.data?.message || err?.message || ''));
      setSnackVisible(true);
    } finally {
      setIsTranscribing(false);
    }
  };

  // ── PanResponder：按住说话 + 上滑取消 ──
  const micPanResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onStartShouldSetPanResponderCapture: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderGrant: () => {
        startRecording();
      },
      onPanResponderMove: (_evt, gesture) => {
        // dy < -60：手指上滑超过 60px，标记为"即将取消"
        const cancelNow = gesture.dy < -60;
        if (cancelNow !== willCancelRef.current) {
          willCancelRef.current = cancelNow;
          setWillCancel(cancelNow);
        }
      },
      onPanResponderRelease: () => {
        stopRecording({ cancel: willCancelRef.current });
      },
      onPanResponderTerminate: () => {
        // 被其他组件抢走响应（比如系统来电），取消录音
        stopRecording({ cancel: true });
      },
    })
  ).current;

  // 组件卸载时兜底清理录音
  useEffect(() => {
    return () => {
      if (recordTimerRef.current) clearInterval(recordTimerRef.current);
      const rec = recordingRef.current;
      if (rec) {
        rec.stopAndUnloadAsync().catch(() => {});
        recordingRef.current = null;
      }
    };
  }, []);

  // 选择图片
  const pickImage = async (useCamera) => {
    const options = {
      mediaTypes: ['images'],
      quality: 0.7,
      base64: true,
      allowsEditing: true,
    };
    let result;
    if (useCamera) {
      const { status } = await ImagePicker.requestCameraPermissionsAsync();
      if (status !== 'granted') {
        setSnackMsg('需要相机权限');
        setSnackVisible(true);
        return;
      }
      result = await ImagePicker.launchCameraAsync(options);
    } else {
      result = await ImagePicker.launchImageLibraryAsync(options);
    }
    if (!result.canceled && result.assets?.[0]) {
      const asset = result.assets[0];
      setSelectedImage({ uri: asset.uri, base64: asset.base64 });
    }
  };

  const handleImagePick = () => {
    if (Platform.OS === 'web') {
      pickImage(false);
    } else {
      Alert.alert('选择图片', '请选择图片来源', [
        { text: '拍照', onPress: () => pickImage(true) },
        { text: '从相册选择', onPress: () => pickImage(false) },
        { text: '取消', style: 'cancel' },
      ]);
    }
  };

  // 公式预渲染 + 双写缓存的辅助函数
  const prerenderAndPersist = async (content, msgId) => {
    const segs = prerenderMessage(content, 15);
    setCached(content, segs, 15).catch(() => {});
    if (msgId) {
      const meta = JSON.stringify(segs);
      patchMessageRender(msgId, { contentHtml: meta, renderMeta: meta }).catch(() => {});
    }
  };

  // 发送
  const handleSend = async () => {
    const hasText = inputValue.trim().length > 0;
    const hasImage = selectedImage?.base64;
    if (!hasText && !hasImage) return;

    const userMsg = hasText ? inputValue : '请解答图片中的题目';
    setInputValue('');
    const imageUri = selectedImage?.uri;
    const imageBase64 = selectedImage?.base64;
    setSelectedImage(null);

    setMessages((prev) => [...prev, {
      role: 'user',
      content: userMsg,
      imageBase64: imageBase64 ? '1' : null,
      _localImageUri: imageUri,
    }]);

    setLoading(true);
    try {
      const payload = { sessionId, message: userMsg };
      if (imageBase64) payload.image = imageBase64;

      // 图片消息：同步调用（保持原有逻辑）
      if (imageBase64) {
        const res = await sendMessage(payload);
        if (!sessionId && res.data.sessionId) {
          setSessionId(res.data.sessionId);
          if (selectedKbId) bindSessionKnowledgeBase(res.data.sessionId, selectedKbId).catch(() => {});
        }
        const aiContent = res.data.content;
        const aiId = res.data.id;
        setMessages((prev) => [...prev, { role: 'assistant', content: aiContent, id: aiId }]);
        if (aiContent && hasMath(aiContent)) {
          prerenderAndPersist(aiContent, aiId);
        }
      } else {
        // 文本消息：流式 SSE
        streamingRef.current = true;
        const msgIdx = messages.length + 1;
        setMessages((prev) => [...prev, { role: 'assistant', content: '', reasoningContent: '', id: null, _streaming: true }]);

        await sendMessageStream(payload,
          // onToken
          (token) => {
            setMessages((prev) => {
              const list = [...prev];
              const last = list[list.length - 1];
              if (last && last._streaming) {
                list[list.length - 1] = { ...last, content: last.content + token };
              }
              return list;
            });
          },
          // onError
          (err) => {
            setMessages((prev) => {
              const list = [...prev];
              const last = list[list.length - 1];
              if (last && last._streaming) {
                list[list.length - 1] = { ...last, content: '发送失败：' + (err?.message || '未知错误'), _streaming: false };
              }
              return list;
            });
            setSnackMsg('发送失败：' + (err?.message || '请重试'));
            setSnackVisible(true);
          },
          // onDone
          () => {
            if (sessionId) return; // 已有 session
            setMessages((prev) => {
              const list = [...prev];
              const last = list[list.length - 1];
              if (last && last._streaming) {
                list[list.length - 1] = { ...last, _streaming: false };
                if (last.content && hasMath(last.content)) {
                  prerenderAndPersist(last.content, last.id).catch(() => {});
                }
              }
              return list;
            });
          },
          // onSession
          (sessionData) => {
            if (sessionData?.sessionId && !sessionId) {
              setSessionId(sessionData.sessionId);
            }
            if (typeof sessionData?.thinkingEnabled === 'boolean') {
              setThinkingEnabled(sessionData.thinkingEnabled);
            }
          },
          // onReasoning
          (token) => {
            setMessages((prev) => {
              const list = [...prev];
              const last = list[list.length - 1];
              if (last && last._streaming) {
                list[list.length - 1] = { ...last, reasoningContent: (last.reasoningContent || '') + token };
              }
              return list;
            });
          }
        );
      }
    } catch (err) {
      const backendMsg = err?.response?.data?.message;
      setSnackMsg('发送失败：' + (backendMsg || err?.message || '请重试'));
      setSnackVisible(true);
    } finally {
      setLoading(false);
      streamingRef.current = false;
    }
  };

  // 消息气泡（正向 FlatList，index 即时序索引）
  const renderMessage = ({ item, index }) => {
    const isUser = item.role === 'user';
    const isTtsLoading = ttsLoadingIndex === index;
    const isTtsPlaying = ttsPlayingIndex === index;
    const hasText = !!item.content && item.content.trim().length > 0;

    return (
      <View style={[styles.msgRow, isUser ? styles.msgRowUser : styles.msgRowBot]}>
        <View style={[styles.msgColumn, !isUser && styles.msgColumnBot]}>
            <View
              style={[
                styles.msgBubble,
                isUser ? styles.msgBubbleUser : styles.msgBubbleBot,
              ]}
            >
              {(item._localImageUri || item.imageBase64) && (
                <Image
                  source={{
                    uri: item._localImageUri || `data:image/jpeg;base64,${item.imageBase64}`,
                  }}
                  style={styles.msgImage}
                  resizeMode="contain"
                />
              )}
              {!isUser && item.reasoningContent && item.reasoningContent.trim().length > 0 && (
                <TouchableOpacity
                  activeOpacity={0.7}
                  style={{ flexDirection: 'row', alignItems: 'center', padding: 6, backgroundColor: '#F0F0F5', borderRadius: 6, marginBottom: 4 }}
                  onPress={() => setExpandedReasoning(prev => ({ ...prev, [index]: !prev[index] }))}
                >
                  <RNText style={{ fontSize: 12, marginRight: 4 }}>🧠</RNText>
                  <RNText style={{ fontSize: 11, color: '#666' }}>思考过程</RNText>
                </TouchableOpacity>
              )}
              {!isUser && expandedReasoning[index] && item.reasoningContent && (
                <View style={{ padding: 8, backgroundColor: '#F9F9FB', borderRadius: 6, borderLeftWidth: 3, borderLeftColor: '#ccc', marginBottom: 4 }}>
                  <RNText style={{ fontSize: 12, color: '#555', lineHeight: 18 }}>{item.reasoningContent}</RNText>
                </View>
              )}
              {!!item.content && (
                <MathText
                  value={item.content}
                  style={[styles.msgText, isUser && styles.msgTextUser, styles.mathTextWrap]}
                  contentHtml={item.contentHtml}
                />
              )}
            </View>

          {/* AI 回复下方：复制 + 朗读 + 更多 */}
          {!isUser && hasText && (
            <View style={styles.msgActionsRow}>
              {/* 复制全文 */}
              <TouchableOpacity
                onPress={async () => {
                  await Clipboard.setStringAsync(item.content);
                  setSnackMsg('已复制');
                  setSnackVisible(true);
                }}
                activeOpacity={0.7}
                style={styles.msgActionBtn}
              >
                <RNText style={styles.msgActionIcon}>📋</RNText>
              </TouchableOpacity>
              {/* 朗读 */}
              <TouchableOpacity
                onPress={() => handleToggleTts(item.content, index)}
                disabled={isTtsLoading}
                activeOpacity={0.7}
                style={[
                  styles.msgActionBtn,
                  isTtsPlaying && styles.msgActionBtnActive,
                ]}
              >
                {isTtsLoading ? (
                  <ActivityIndicator size={12} color={colors.primary} />
                ) : (
                  <RNText style={styles.msgActionIcon}>
                    {isTtsPlaying ? '⏸' : '🔊'}
                  </RNText>
                )}
              </TouchableOpacity>
              {/* 更多 */}
              <TouchableOpacity
                onPress={() => showPopover(item, index)}
                activeOpacity={0.7}
                style={[
                  styles.msgActionBtn,
                  popoverVisible && popoverMsg?.index === index && styles.msgActionBtnActive,
                ]}
              >
                <RNText style={styles.msgActionIcon}>⋯</RNText>
              </TouchableOpacity>
            </View>
          )}
        </View>
      </View>
    );
  };

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === 'ios' ? 'padding' : undefined}
      keyboardVerticalOffset={Platform.OS === 'ios' ? 90 : 0}
    >
      {/* 顶部：知识库接入状态 */}
      <View style={styles.kbBar}>
        <View style={{ flexDirection: 'row', gap: 8, alignItems: 'center' }}>
          <TouchableOpacity
            style={[styles.kbChip, selectedKb && styles.kbChipActive]}
            activeOpacity={0.75}
            onPress={() => setKbDialogVisible(true)}
          >
            <RNText style={styles.kbChipIcon}>📚</RNText>
            <Text
              style={[styles.kbChipText, selectedKb && styles.kbChipTextActive]}
              numberOfLines={1}
            >
              {selectedKb ? `已接入：${selectedKb.name}` : '未接入知识库'}
            </Text>
            <RNText style={styles.kbChipChevron}>⌄</RNText>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.kbChip, thinkingEnabled && styles.kbChipActive]}
            activeOpacity={0.75}
            onPress={handleToggleThinking}
          >
            <RNText style={styles.kbChipIcon}>{thinkingEnabled ? '🧠' : '💡'}</RNText>
            <Text
              style={[styles.kbChipText, thinkingEnabled && styles.kbChipTextActive]}
              numberOfLines={1}
            >
              {thinkingEnabled ? '深度思考' : '普通模式'}
            </Text>
          </TouchableOpacity>
        </View>
      </View>

      {loadingHistory ? (
        <ActivityIndicator style={{ marginTop: 40 }} color={colors.primary} />
      ) : messages.length === 0 ? (
        <View style={styles.emptyChat}>
          <RNText style={styles.emptyIcon}>🎓</RNText>
          <Text style={styles.emptyTitle}>你好！我是 AI 考研助手</Text>
          <Text style={styles.emptySub}>
            我可以回答你上传资料中的问题，也能为你讲解拍照上传的题目
          </Text>

          <View style={styles.tipGrid}>
            <TouchableOpacity
              style={styles.tipCard}
              onPress={() => setInputValue('帮我讲解一下408数据结构中的红黑树')}
            >
              <RNText style={styles.tipIcon}>📚</RNText>
              <Text style={styles.tipText}>讲解知识点</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.tipCard} onPress={handleImagePick}>
              <RNText style={styles.tipIcon}>📷</RNText>
              <Text style={styles.tipText}>拍照搜题</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.tipCard}
              onPress={() => setInputValue('给我出一道动态规划的题目并讲解')}
            >
              <RNText style={styles.tipIcon}>✏️</RNText>
              <Text style={styles.tipText}>出题练习</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={styles.tipCard}
              onPress={() => setInputValue('我现在应该如何规划剩下的复习时间？')}
            >
              <RNText style={styles.tipIcon}>🗓️</RNText>
              <Text style={styles.tipText}>备考规划</Text>
            </TouchableOpacity>
          </View>
        </View>
      ) : (
        <FlatList
          ref={listRef}
          // 正向渲染：data[0] = 最早消息，data[length-1] = 最新消息
          // 滚动到底部由 useEffect 监听 messages.length 触发，不再用 inverted。
          // 之所以不用 inverted —— 长按 Text 拉起 selection handles 时，
          // Android textActionMode 弹起会让父级布局抖动，inverted 列表反向重算
          // contentOffset 会出现"位置跳走"的视觉错位。
          data={messages}
          keyExtractor={(_, idx) => `m-${idx}`}
          renderItem={renderMessage}
          contentContainerStyle={styles.msgList}
          showsVerticalScrollIndicator={false}
          // 关闭子视图裁剪，避免 Android 上滚动出屏幕的气泡被回收，
          // 影响 Text selectable 的长按手势识别
          removeClippedSubviews={false}
          // 显式禁用自动 inset 调整（iOS），避免 selection toolbar 弹起时
          // FlatList 因 safeAreaInset 重算而瞬间偏移
          contentInsetAdjustmentBehavior="never"
          // 首次加载 3s 内，WebView 公式段测高导致内容尺寸变化时自动追底
          // 3s 后停止 → 用户自由滑动不再受任何自动滚动干扰
          onContentSizeChange={() => {
            if (scrollSettleRef.current != null) {
              listRef.current?.scrollToEnd({ animated: false });
            }
          }}
        />
      )}

      {/* AI 思考中指示 */}
      {loading && (
        <View style={styles.typingRow}>
          <View style={styles.botAvatar}>
            <RNText style={{ fontSize: 16 }}>🤖</RNText>
          </View>
          <View style={styles.typingBubble}>
            <ActivityIndicator size="small" color={colors.primary} />
            <Text style={styles.typingText}>AI 正在思考…</Text>
          </View>
        </View>
      )}

      {/* 图片预览 */}
      {selectedImage && (
        <View style={styles.imagePreview}>
          <Image source={{ uri: selectedImage.uri }} style={styles.previewImg} resizeMode="cover" />
          <IconButton
            icon="close-circle"
            size={20}
            iconColor={colors.danger}
            style={styles.previewClose}
            onPress={() => setSelectedImage(null)}
          />
        </View>
      )}

      {/* 录音中：居中浮层提示 */}
      {isRecording && (
        <View style={styles.recordingOverlay} pointerEvents="none">
          <Animated.View
            style={[
              styles.recordMicWrap,
              { transform: [{ scale: pulseAnim }] },
              willCancel && styles.recordMicWrapCancel,
            ]}
          >
            <RNText style={styles.recordMicIcon}>{willCancel ? '🚫' : '🎙️'}</RNText>
          </Animated.View>
          <Text style={[styles.recordHint, willCancel && styles.recordHintCancel]}>
            {willCancel ? '松开取消识别' : '正在录音… 松开发送，上滑取消'}
          </Text>
          <Text style={styles.recordTimer}>{String(recordSec).padStart(2, '0')} s</Text>
        </View>
      )}

      {/* 转写中：覆盖式 loading */}
      {isTranscribing && (
        <View style={styles.transcribingBar}>
          <ActivityIndicator size="small" color={colors.primary} />
          <Text style={styles.transcribingText}>正在识别语音…</Text>
        </View>
      )}

      {/* 输入栏 */}
      <View style={styles.inputRow}>
        <TouchableOpacity onPress={handleImagePick} style={styles.iconBtn} disabled={isRecording}>
          <RNText style={{ fontSize: 22 }}>📷</RNText>
        </TouchableOpacity>

        {/* 麦克风：按住开始，松开结束；上滑 60px 以上则取消识别 */}
        <View
          style={[
            styles.iconBtn,
            isRecording && (willCancel ? styles.iconBtnCancel : styles.iconBtnRecording),
            (isTranscribing || loading) && { opacity: 0.4 },
          ]}
          {...(!(isTranscribing || loading) ? micPanResponder.panHandlers : {})}
        >
          <RNText style={{ fontSize: 20 }}>
            {isRecording ? (willCancel ? '🚫' : '🔴') : '🎤'}
          </RNText>
        </View>

        <TextInput
          value={inputValue}
          onChangeText={setInputValue}
          placeholder={
            isRecording ? '🎙️ 正在录音...' :
            isTranscribing ? '🧠 识别中...' :
            selectedImage ? '描述题目要求（可选）...' :
            '输入你的问题...'
          }
          placeholderTextColor={colors.textTertiary}
          multiline
          style={styles.input}
          editable={!isRecording && !isTranscribing}
        />
        <TouchableOpacity
          onPress={handleSend}
          disabled={loading || isRecording || isTranscribing || (!inputValue.trim() && !selectedImage)}
          style={[
            styles.sendBtn,
            (loading || isRecording || isTranscribing || (!inputValue.trim() && !selectedImage)) && styles.sendBtnDisabled,
          ]}
        >
          <RNText style={styles.sendBtnText}>发送</RNText>
        </TouchableOpacity>
      </View>

      {/* 知识库选择弹窗 */}
      <Portal>
        <Dialog visible={kbDialogVisible} onDismiss={() => setKbDialogVisible(false)}>
          <Dialog.Title>为本次对话选择知识库</Dialog.Title>
          <Dialog.ScrollArea style={{ paddingHorizontal: 0, maxHeight: 400 }}>
            <RadioButton.Group
              onValueChange={(v) => handlePickKb(v === 'none' ? null : Number(v))}
              value={selectedKbId ? String(selectedKbId) : 'none'}
            >
              <RadioButton.Item
                label="不接入（不使用知识库资料）"
                value="none"
                labelStyle={{ fontSize: 14 }}
              />
              {kbList.map((kb) => (
                <RadioButton.Item
                  key={kb.id}
                  label={`${kb.name}  ·  ${kb.enabledCount || 0}/${kb.documentCount || 0} 启用`}
                  value={String(kb.id)}
                  labelStyle={{ fontSize: 14 }}
                />
              ))}
              {kbList.length === 0 && (
                <Text style={{ padding: 16, color: colors.textTertiary }}>
                  你还没有知识库，去「知识库」页面新建一个吧
                </Text>
              )}
            </RadioButton.Group>
          </Dialog.ScrollArea>
          <Dialog.Actions>
            <Button onPress={() => setKbDialogVisible(false)}>关闭</Button>
          </Dialog.Actions>
        </Dialog>
      </Portal>

      {/* 消息操作弹窗（底部居中） */}
      <Portal>
        {popoverVisible && (
          <View style={StyleSheet.absoluteFill} pointerEvents="box-none">
            <TouchableOpacity
              style={StyleSheet.absoluteFill}
              activeOpacity={1}
              onPress={dismissPopover}
            >
              <View />
            </TouchableOpacity>
            <View style={styles.popoverWrapper}>
              <View style={styles.popoverBar}>
                <TouchableOpacity
                  style={styles.popoverBtn}
                  onPress={handlePopoverFavorite}
                  activeOpacity={0.7}
                >
                  <RNText style={styles.popoverIcon}>⭐</RNText>
                  <RNText style={styles.popoverLabel}>收藏</RNText>
                </TouchableOpacity>
                <TouchableOpacity
                  style={styles.popoverBtn}
                  onPress={handlePopoverShare}
                  activeOpacity={0.7}
                >
                  <RNText style={styles.popoverIcon}>📤</RNText>
                  <RNText style={styles.popoverLabel}>分享</RNText>
                </TouchableOpacity>
              </View>
            </View>
          </View>
        )}
      </Portal>

      <Snackbar
        visible={snackVisible}
        onDismiss={() => setSnackVisible(false)}
        duration={8000}
        action={{ label: '关闭', onPress: () => setSnackVisible(false) }}
      >
        {snackMsg}
      </Snackbar>
    </KeyboardAvoidingView>
  );
}

const styles = StyleSheet.create({
  container: { flex: 1, backgroundColor: colors.background },

  // 顶部知识库接入栏
  kbBar: {
    paddingHorizontal: spacing.lg,
    paddingTop: spacing.sm,
    paddingBottom: spacing.sm,
    backgroundColor: colors.background,
    borderBottomWidth: 1,
    borderBottomColor: colors.borderLight,
  },
  kbChip: {
    alignSelf: 'flex-start',
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: radii.pill,
    borderWidth: 1,
    borderColor: colors.border,
    backgroundColor: colors.surface,
    maxWidth: '80%',
  },
  kbChipActive: {
    borderColor: colors.primary,
    backgroundColor: colors.primarySoft,
  },
  kbChipIcon: { fontSize: 14, marginRight: 6 },
  kbChipText: {
    ...typography.caption,
    color: colors.textSecondary,
    fontWeight: '600',
    maxWidth: 220,
  },
  kbChipTextActive: { color: colors.primaryDark },
  kbChipChevron: {
    fontSize: 14,
    color: colors.textTertiary,
    marginLeft: 6,
    marginTop: -2,
  },


  // Empty
  emptyChat: {
    flex: 1,
    alignItems: 'center',
    padding: spacing['2xl'],
    paddingTop: spacing['3xl'],
  },
  emptyIcon: { fontSize: 56, marginBottom: spacing.lg },
  emptyTitle: { ...typography.titleLg, color: colors.textPrimary, marginBottom: spacing.xs },
  emptySub: {
    ...typography.bodySm,
    color: colors.textSecondary,
    textAlign: 'center',
    marginBottom: spacing['2xl'],
    paddingHorizontal: spacing.xl,
  },
  tipGrid: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: spacing.md,
    justifyContent: 'center',
  },
  tipCard: {
    width: '45%',
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    borderWidth: 1,
    borderColor: colors.border,
    padding: spacing.md,
    alignItems: 'center',
  },
  tipIcon: { fontSize: 24, marginBottom: 6 },
  tipText: { ...typography.bodySm, color: colors.textPrimary, fontWeight: '600' },

  // Messages
  msgList: { padding: spacing.md, paddingBottom: spacing.sm },
  msgRow: { flexDirection: 'row', marginBottom: spacing.md, alignItems: 'flex-end' },
  msgRowUser: { justifyContent: 'flex-end' },
  msgRowBot: { justifyContent: 'flex-start' },
  msgColumn: {
    maxWidth: '78%',
    flexShrink: 1,
  },
  msgColumnBot: {
    maxWidth: '100%',
  },
  msgBubble: {
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: radii.lg,
  },
  msgBubbleUser: {
    backgroundColor: colors.primary,
    borderBottomRightRadius: 4,
  },
  msgBubbleBot: {
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderBottomLeftRadius: 4,
  },
  msgText: {
    ...typography.bodyMd,
    color: colors.textPrimary,
    lineHeight: 22,
  },
  msgTextUser: {
    color: '#FFFFFF',
  },
  mathTextWrap: {
    marginTop: 2,
  },
  msgImage: {
    width: 200,
    height: 200,
    borderRadius: radii.md,
    marginBottom: 6,
  },

  // AI 回复下方操作按钮行（复制 + 朗读）
  msgActionsRow: {
    flexDirection: 'row',
    alignItems: 'center',
    marginTop: 4,
    gap: 6,
  },
  msgActionBtn: {
    paddingHorizontal: 8,
    paddingVertical: 4,
    borderRadius: radii.pill,
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.borderLight,
  },
  msgActionBtnActive: {
    backgroundColor: colors.primarySoft,
    borderColor: colors.primary,
  },
  msgActionIcon: {
    fontSize: 13,
  },

  // 弹出操作栏（底部居中）
  popoverWrapper: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 100,
    alignItems: 'center',
  },
  popoverBar: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderRadius: radii.lg,
    paddingVertical: 6,
    paddingHorizontal: 8,
    gap: 2,
    ...shadows.md,
  },
  popoverBtn: {
    alignItems: 'center',
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: radii.md,
  },
  popoverIcon: {
    fontSize: 22,
  },
  popoverLabel: {
    fontSize: 10,
    color: colors.textSecondary,
    marginTop: 2,
  },

  // Typing indicator
  typingRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: spacing.md,
    paddingVertical: spacing.sm,
  },
  typingBubble: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderWidth: 1,
    borderColor: colors.border,
    borderRadius: radii.lg,
    paddingHorizontal: 12,
    paddingVertical: 8,
    gap: 6,
  },
  typingText: {
    ...typography.bodySm,
    color: colors.textSecondary,
  },

  // Preview
  imagePreview: {
    flexDirection: 'row',
    alignItems: 'center',
    backgroundColor: colors.surface,
    borderRadius: radii.md,
    padding: 4,
    marginHorizontal: spacing.md,
    marginBottom: 4,
    alignSelf: 'flex-start',
    borderWidth: 1,
    borderColor: colors.border,
  },
  previewImg: { width: 64, height: 64, borderRadius: radii.sm },
  previewClose: { margin: 0 },

  // Input
  inputRow: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    padding: spacing.sm,
    gap: 6,
    backgroundColor: colors.surface,
    borderTopWidth: 1,
    borderTopColor: colors.borderLight,
  },
  iconBtn: {
    width: 40,
    height: 40,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 20,
    backgroundColor: colors.surfaceAlt,
  },
  iconBtnRecording: {
    backgroundColor: colors.dangerSoft || '#FEE2E2',
    borderWidth: 2,
    borderColor: colors.danger || '#EF4444',
  },
  iconBtnCancel: {
    backgroundColor: '#1F2937',
    borderWidth: 2,
    borderColor: '#4B5563',
  },

  // 语音录制浮层（屏幕下半部居中显示）
  recordingOverlay: {
    position: 'absolute',
    left: 0,
    right: 0,
    bottom: 90,
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 20,
  },
  recordMicWrap: {
    width: 110,
    height: 110,
    borderRadius: 55,
    backgroundColor: colors.primary,
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 12,
    ...shadows.colored,
  },
  recordMicWrapCancel: {
    backgroundColor: '#1F2937',
  },
  recordMicIcon: {
    fontSize: 48,
  },
  recordHint: {
    ...typography.bodyMd,
    color: colors.textPrimary,
    backgroundColor: colors.surface,
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: radii.pill,
    borderWidth: 1,
    borderColor: colors.border,
  },
  recordHintCancel: {
    color: '#FFFFFF',
    backgroundColor: '#991B1B',
    borderColor: '#7F1D1D',
  },
  recordTimer: {
    marginTop: 6,
    ...typography.caption,
    color: colors.primary,
    fontWeight: '700',
    fontSize: 13,
  },

  // 识别中的状态条
  transcribingBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 8,
    backgroundColor: colors.surface,
    paddingHorizontal: spacing.md,
    paddingVertical: 8,
    borderTopWidth: 1,
    borderTopColor: colors.borderLight,
  },
  transcribingText: {
    ...typography.bodySm,
    color: colors.textSecondary,
  },
  input: {
    flex: 1,
    backgroundColor: colors.surfaceAlt,
    borderRadius: radii.lg,
    paddingHorizontal: 14,
    paddingVertical: Platform.OS === 'ios' ? 10 : 8,
    maxHeight: 100,
    color: colors.textPrimary,
    fontSize: 15,
  },
  sendBtn: {
    backgroundColor: colors.primary,
    paddingHorizontal: 16,
    height: 40,
    borderRadius: 20,
    alignItems: 'center',
    justifyContent: 'center',
    ...shadows.colored,
  },
  sendBtnDisabled: {
    backgroundColor: colors.textTertiary,
    opacity: 0.5,
  },
  sendBtnText: {
    color: '#FFFFFF',
    fontWeight: '700',
    fontSize: 14,
    letterSpacing: 1,
  },
});
