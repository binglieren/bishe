import React, { useEffect, useState } from 'react';
import { View, StyleSheet, FlatList } from 'react-native';
import { Text, Card, Button, Chip, Snackbar } from 'react-native-paper';
import * as DocumentPicker from 'expo-document-picker';
import { uploadDocument, getDocuments, deleteDocument } from '../api/document';

const statusMap = {
  PROCESSING: { color: '#1677ff', text: '处理中' },
  COMPLETED: { color: '#52c41a', text: '已完成' },
  FAILED: { color: '#f5222d', text: '处理失败' },
};

export default function DocumentScreen() {
  const [documents, setDocuments] = useState([]);
  const [uploading, setUploading] = useState(false);
  const [snackVisible, setSnackVisible] = useState(false);
  const [snackMsg, setSnackMsg] = useState('');

  const loadDocuments = async () => {
    try {
      const res = await getDocuments();
      setDocuments(res.data || []);
    } catch (err) {
      console.error(err);
    }
  };

  useEffect(() => { loadDocuments(); }, []);

  const handleUpload = async () => {
    try {
      const result = await DocumentPicker.getDocumentAsync({
        type: ['application/pdf', 'text/plain'],
        copyToCacheDirectory: true,
      });
      if (result.canceled) return;
      setUploading(true);
      const file = result.assets[0];
      const formData = new FormData();
      formData.append('file', {
        uri: file.uri,
        name: file.name,
        type: file.mimeType || 'application/octet-stream',
      });
      await uploadDocument(formData);
      setSnackMsg('上传成功，正在处理中');
      setSnackVisible(true);
      loadDocuments();
    } catch (err) {
      setSnackMsg('上传失败');
      setSnackVisible(true);
    } finally {
      setUploading(false);
    }
  };

  const handleDelete = async (id) => {
    try {
      await deleteDocument(id);
      setSnackMsg('删除成功');
      setSnackVisible(true);
      loadDocuments();
    } catch (err) {
      setSnackMsg('删除失败');
      setSnackVisible(true);
    }
  };

  const renderDocument = ({ item }) => (
    <Card style={styles.card}>
      <Card.Content>
        <View style={styles.row}>
          <Text style={styles.fileName}>📄 {item.originalFilename}</Text>
          <Chip
            mode="outlined"
            textStyle={{ color: statusMap[item.status]?.color || '#999' }}
          >
            {statusMap[item.status]?.text || item.status}
          </Chip>
        </View>
        <Text style={styles.info}>
          大小: {item.fileSize ? `${(item.fileSize / 1024).toFixed(1)} KB` : '-'} | 上传时间: {item.uploadTime || '-'}
        </Text>
      </Card.Content>
      <Card.Actions>
        <Button
          mode="text"
          textColor="#f5222d"
          onPress={() => handleDelete(item.id)}
        >
          删除
        </Button>
      </Card.Actions>
    </Card>
  );

  return (
    <View style={styles.container}>
      <Card style={styles.uploadCard}>
        <Card.Content>
          <Button
            mode="contained"
            icon="upload"
            onPress={handleUpload}
            loading={uploading}
          >
            上传学习资料
          </Button>
          <Text style={styles.hint}>支持 PDF、TXT 格式，上传后将自动向量化，可在 AI 问答中使用</Text>
        </Card.Content>
      </Card>

      <FlatList
        data={documents}
        keyExtractor={(item) => String(item.id)}
        renderItem={renderDocument}
        contentContainerStyle={styles.list}
      />

      <Snackbar visible={snackVisible} onDismiss={() => setSnackVisible(false)} duration={2000}>
        {snackMsg}
      </Snackbar>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
    padding: 12,
  },
  uploadCard: {
    marginBottom: 12,
  },
  hint: {
    color: '#999',
    fontSize: 12,
    marginTop: 8,
    textAlign: 'center',
  },
  list: {
    paddingBottom: 12,
  },
  card: {
    marginBottom: 10,
  },
  row: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 4,
  },
  fileName: {
    fontSize: 15,
    fontWeight: '600',
    flex: 1,
  },
  info: {
    fontSize: 12,
    color: '#999',
  },
});